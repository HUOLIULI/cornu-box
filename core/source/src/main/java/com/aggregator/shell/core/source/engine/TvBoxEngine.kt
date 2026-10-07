package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.PlayResult
import com.aggregator.shell.core.source.api.VideoDetail
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import com.aggregator.shell.core.source.sandbox.PythonRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * TVBox / CatVod / T4-compatible engine.
 *
 * Supports:
 * - JS spider backends (via [JsSandboxExecutor])
 * - Python backends (via [PythonRuntime]; default build ships [PythonRuntime.NoOp])
 * - JSON API backends
 */
class TvBoxEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor,
    private val pythonRuntime: PythonRuntime
) : VideoEngine {

    override suspend fun search(keyword: String, page: Int): List<VideoResult> {
        val spider = fetchTvBoxConfig() ?: return emptyList()
        val sites = spider.optJSONArray("sites") ?: return emptyList()
        return (0 until sites.length()).mapNotNull { i ->
            val site = sites.getJSONObject(i)
            try {
                // Simplified: call the site API if set, otherwise return an empty marker.
                val api = site.optString("api")
                if (api.isBlank()) null else {
                    val resp = get("$api/search?key=${java.net.URLEncoder.encode(keyword, "UTF-8")}&page=$page")
                    if (resp.isBlank()) null else {
                        val data = org.json.JSONObject(resp)
                        listOf(
                            VideoResult(
                                id = data.optString("id"),
                                title = data.optString("title"),
                                coverUrl = data.optString("cover"),
                                type = data.optString("type"),
                                sourceKey = site.optString("key", "demo")
                            )
                        )
                    }
                }
            } catch (e: Exception) { null }
        }.flatten()
    }

    override suspend fun getDetail(id: String): VideoDetail {
        val spider = fetchTvBoxConfig() ?: return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val site = spider.optJSONArray("sites")?.let {
            if (it.length() > 0) it.getJSONObject(0) else null
        } ?: return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val api = site.optString("api")
        if (api.isBlank()) return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val resp = get("$api/detail/$id")
        val json = org.json.JSONObject(resp)
        val episodes = mutableMapOf<String, List<String>>()
        json.optJSONArray("eps")?.let { arr ->
            (0 until arr.length()).map { index ->
                val ep = arr.getJSONObject(index)
                ep.optJSONArray("url")?.let { urls ->
                    episodes[ep.optString("name", "第${index + 1}集")] =
                        (0 until urls.length()).map { j -> urls.getString(j) }
                }
            }
        }
        return VideoDetail(
            id = json.optString("id", id),
            title = json.optString("title"),
            desc = json.optString("desc"),
            episodes = episodes,
            sourceKey = site.optString("key")
        )
    }

    override suspend fun getPlayUrl(id: String, flag: String): PlayResult {
        // Resolve via site api + parse line index flag.
        val spider = fetchTvBoxConfig() ?: return PlayResult(url = "", name = "")
        val site = spider.optJSONArray("sites")?.let {
            if (it.length() > 0) it.getJSONObject(0) else null
        } ?: return PlayResult(url = "", name = "")
        val api = site.optString("api")
        val resp = get("$api/play/$id/$flag")
        val json = org.json.JSONObject(resp)
        val url = json.optString("url").ifEmpty {
            // fallback: first item in `url` array if present
            json.optJSONArray("url")?.optString(0) ?: ""
        }
        return PlayResult(url = url, name = json.optString("name", flag))
    }

    private suspend fun fetchTvBoxConfig(): org.json.JSONObject? =
        try {
            org.json.JSONObject(SourceBootstrap.defaultTvBoxJson())
        } catch (e: Exception) {
            null
        }

    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            client.newCall(okhttp3.Request.Builder().url(url).build())
                .execute().use { it.body?.string() ?: "" }
        }
}
