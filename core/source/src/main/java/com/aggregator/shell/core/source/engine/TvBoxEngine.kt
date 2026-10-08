package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.common.AppLog
import com.aggregator.shell.core.common.NoOpLog
import com.aggregator.shell.core.data.local.LiveSourceDao
import com.aggregator.shell.core.data.local.VideoSourceDao
import com.aggregator.shell.core.source.api.PlayResult
import com.aggregator.shell.core.source.api.VideoDetail
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import com.aggregator.shell.core.source.sandbox.PythonRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * TVBox / CatVod / T4-compatible engine.
 *
 * Supports:
 * - JS spider backends (via [JsSandboxExecutor])
 * - Python backends (via [PythonRuntime]; default build ships [PythonRuntime.NoOp])
 * - JSON API backends
 *
 * Source payloads come from Room [videoSourceDao] / [liveSourceDao] when the
 * user has imported subscriptions; otherwise the built-in demo config is used.
 */
class TvBoxEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor,
    private val pythonRuntime: PythonRuntime,
    private val videoSourceDao: VideoSourceDao? = null,
    private val liveSourceDao: LiveSourceDao? = null,
    private val log: AppLog = NoOpLog
) : VideoEngine {

    override suspend fun search(keyword: String, page: Int): List<VideoResult> {
        val sites = resolveSites()
        if (sites.isEmpty()) {
            log.w(TAG, "no video sites available")
            return emptyList()
        }
        return sites.mapNotNull { site ->
            val api = site.optString("api")
            if (api.isBlank()) {
                log.w(TAG, "site ${site.optString("name")} has no api url")
                return@mapNotNull null
            }
            try {
                val resp = get("$api/search?key=${java.net.URLEncoder.encode(keyword, "UTF-8")}&page=$page")
                if (resp.isBlank()) return@mapNotNull null
                parseVideoList(resp, site)
            } catch (e: Exception) {
                log.e(TAG, "search failed for ${site.optString("name")}", e)
                null
            }
        }.flatten()
    }

    /**
     * TVBox / CatVod search responses wrap the result in a JSON object whose
     * `list` (or `videoList`) field holds the items. Some backends return a
     * bare array. Each element carries `vod_id` / `vod_name` / `vod_pic` in
     * the TVBox naming scheme.
     */
    private fun parseVideoList(resp: String, site: JSONObject): List<VideoResult> {
        val json = JSONObject(resp)
        val arr: JSONArray = when {
            json.has("list") -> json.getJSONArray("list")
            json.has("videoList") -> json.getJSONArray("videoList")
            json.has("data") && json.get("data") is JSONArray -> json.getJSONArray("data")
            else -> JSONArray(resp)
        }
        val results = mutableListOf<VideoResult>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            results.add(
                VideoResult(
                    id = item.optString("vod_id").ifEmpty { item.optString("id") },
                    title = item.optString("vod_name").ifEmpty { item.optString("name") },
                    coverUrl = item.optString("vod_pic").ifEmpty { item.optString("pic") },
                    type = item.optString("type").ifEmpty { item.optString("type_name") },
                    year = item.optString("year").ifEmpty { item.optString("area") },
                    sourceKey = site.optString("key", "demo")
                )
            )
        }
        return results
    }

    override suspend fun getDetail(id: String): VideoDetail {
        val site = resolveSites().firstOrNull()
            ?: run {
                log.w(TAG, "no site for detail $id")
                return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
            }
        val api = site.optString("api")
        if (api.isBlank()) return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val resp = get("$api/detail/$id")
        val json = runCatching { JSONObject(resp) }.getOrNull()
            ?: return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val episodes = parseEpisodes(json)
        return VideoDetail(
            id = json.optString("id", id),
            title = json.optString("title").ifEmpty { json.optString("vod_name") },
            desc = json.optString("desc").ifEmpty { json.optString("vod_content") },
            episodes = episodes,
            sourceKey = site.optString("key")
        )
    }

    /**
     * TVBox detail responses keep episodes in a `episodes` object (line-name ->
     * "1|...|2|..." split strings) or a `eps` array of {name, url[]}. Handle both.
     */
    private fun parseEpisodes(json: JSONObject): Map<String, List<String>> {
        val episodes = mutableMapOf<String, List<String>>()
        json.optJSONArray("eps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val ep = arr.optJSONObject(i) ?: continue
                ep.optJSONArray("url")?.let { urls ->
                    episodes[ep.optString("name", "第${i + 1}集")] =
                        (0 until urls.length()).map { urls.getString(it) }
                }
            }
        }
        json.optJSONObject("episodes")?.let { epsObj ->
            for (key in epsObj.keys()) {
                val line = epsObj.optString(key)
                episodes[key] = line.split("§").filter { it.isNotBlank() }
            }
        }
        return episodes
    }

    override suspend fun getPlayUrl(id: String, flag: String): PlayResult {
        val site = resolveSites().firstOrNull()
            ?: return PlayResult(url = "", name = "")
        val api = site.optString("api")
        val resp = get("$api/play/$id/$flag")
        val json = runCatching { JSONObject(resp) }.getOrNull()
            ?: run {
                log.w(TAG, "play url not JSON: $resp")
                return PlayResult(url = resp, name = flag)
            }
        val url = json.optString("url").ifEmpty {
            json.optJSONArray("url")?.optString(0) ?: ""
        }
        return PlayResult(url = url, name = json.optString("name", flag))
    }

    /**
     * Resolve TVBox site configs from Room (enabled, non-blank api). When the
     * user has imported subscriptions, build a `sites` array from them; the
     * built-in demo is the fallback.
     */
    private suspend fun resolveSites(): List<JSONObject> {
        videoSourceDao?.let { dao ->
            val rows = runCatching { dao.all().first() }.getOrDefault(emptyList())
            val enabled = rows.filter { it.enabled && it.api.isNotBlank() }
            if (enabled.isNotEmpty()) {
                log.i(TAG, "using ${enabled.size} Room video source(s)")
                return enabled.map {
                    JSONObject(it.rawJson).apply { put("api", it.api) }
                }
            }
        }
        log.i(TAG, "falling back to built-in demo tvbox config")
        return runCatching {
            val config = JSONObject(SourceBootstrap.defaultTvBoxJson())
            config.optJSONArray("sites")?.let { (0 until it.length()).map { i -> it.getJSONObject(i) } }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /**
     * Resolve the first live source URL for IPTV-style playback. Reads Room
     * live_sources when present; falls back to the demo config.
     */
    suspend fun resolveLiveUrls(): List<String> {
        liveSourceDao?.let { dao ->
            val rows = runCatching { dao.all().first() }.getOrDefault(emptyList())
            val urls = rows.filter { it.enabled && it.url.isNotBlank() }.map { it.url }
            if (urls.isNotEmpty()) {
                log.i(TAG, "using ${urls.size} Room live source(s)")
                return urls
            }
        }
        log.i(TAG, "falling back to built-in demo live config")
        return runCatching {
            val config = JSONObject(SourceBootstrap.defaultTvBoxJson())
            config.optJSONArray("lives")?.let {
                (0 until it.length()).map { i -> it.getJSONObject(i).optString("url") }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            client.newCall(okhttp3.Request.Builder().url(url).build())
                .execute().use { it.body?.string() ?: "" }
        }

    companion object {
        const val TAG = "TvBoxEngine"
    }
}
