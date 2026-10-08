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
import org.json.JSONObject
import java.net.URLEncoder

/**
 * TVBox / CatVod / T4-compatible engine.
 *
 * 对接 CatVod 标准接口约定：
 * - 搜索：`{api}?ac=search&wd={keyword}&pg={page}`，响应 `{"list":[{vod_id,vod_name,vod_pic,type_name}]}`
 * - 详情：`{api}?ac=detail&ids={id}`，响应 `{"list":[{vod_id,vod_name,vod_content,vod_play_url}]}`
 * - 播放：`{api}?ac=videoplay&ids={id}`，从 `vod_play_url` 解析线路与剧集
 * - `vod_play_url` 格式：`线路1$url#url#url$$$线路2$url#url`（`$$$` 分线路，`#` 分集，`$` 分名称/URL）
 *
 * 内置演示源（example.com）请求不可达时返回本地演示数据，保证开箱有内容。
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
            runCatching {
                val api = site.optString("api")
                if (api.isBlank()) return@runCatching null
                val resp = get(buildUrl(api, "search", mapOf("wd" to keyword, "pg" to page.toString())))
                if (resp.isBlank()) return@runCatching null
                val json = JSONObject(resp)
                val list = json.optJSONArray("list") ?: return@runCatching null
                (0 until list.length()).mapNotNull { j ->
                    val v = list.getJSONObject(j)
                    val title = v.optString("vod_name")
                    if (title.isBlank()) null
                    else VideoResult(
                        id = v.optString("vod_id"),
                        title = title,
                        coverUrl = v.optString("vod_pic"),
                        type = v.optString("type_name"),
                        year = v.optString("vod_year"),
                        sourceKey = site.optString("key", "demo")
                    )
                }
            }.getOrNull()
        }.flatten()
    }

    override suspend fun getDetail(id: String): VideoDetail {
        val site = firstSite() ?: return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val api = site.optString("api")
        if (api.isBlank()) return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val resp = get(buildUrl(api, "detail", mapOf("ids" to id)))
        if (resp.isBlank()) return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val json = JSONObject(resp)
        val vod = json.optJSONArray("list")?.optJSONObject(0)
            ?: json.optJSONObject("vod")
            ?: return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        return VideoDetail(
            id = vod.optString("vod_id", id),
            title = vod.optString("vod_name"),
            desc = vod.optString("vod_content"),
            episodes = parseEpisodes(vod.optString("vod_play_url")),
            sourceKey = site.optString("key")
        )
    }

    override suspend fun getPlayUrl(id: String, flag: String): PlayResult {
        val site = firstSite() ?: return PlayResult(url = "", name = "")
        val api = site.optString("api")
        if (api.isBlank()) return PlayResult(url = "", name = "")
        val resp = get(buildUrl(api, "videoplay", mapOf("ids" to id)))
        if (resp.isBlank()) return PlayResult(url = "", name = "")
        val json = JSONObject(resp)
        val vod = json.optJSONArray("list")?.optJSONObject(0)
            ?: json.optJSONObject("vod")
            ?: return PlayResult(url = "", name = "")
        val episodes = parseEpisodes(vod.optString("vod_play_url"))
        // flag 形如 "线路索引-集数索引"（1 基，如 "1-1"）；缺省取第一线路第一集
        val parts = flag.split("-")
        val lineIdx = (parts.getOrNull(0)?.toIntOrNull()?.minus(1)) ?: 0
        val epIdx = (parts.getOrNull(1)?.toIntOrNull()?.minus(1)) ?: 0
        val lines = episodes.values.toList()
        val line = lines.getOrNull(lineIdx.coerceIn(0, lines.size - 1))
            ?: return PlayResult(url = "", name = flag)
        val url = line.getOrNull(epIdx.coerceIn(0, line.size - 1))
            ?: line.firstOrNull()
            ?: return PlayResult(url = "", name = flag)
        return PlayResult(url = url, name = flag)
    }

    // ---------- 内部 ----------

    private fun parseEpisodes(raw: String): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        if (raw.isBlank()) return result
        for (line in raw.split("\\$\\$\\$")) {
            val parts = line.split("\\$", limit = 2)
            if (parts.size < 2) continue
            val name = parts[0].trim()
            val urls = parts[1].split("#").map { it.trim() }.filter { it.isNotBlank() }
            if (name.isNotEmpty() && urls.isNotEmpty()) result[name] = urls
        }
        return result
    }

    private fun buildUrl(api: String, ac: String, params: Map<String, String>): String {
        val sb = StringBuilder(api)
        sb.append(if (api.contains("?")) "&" else "?").append("ac=").append(ac)
        for ((k, v) in params) {
            sb.append("&").append(k).append("=").append(URLEncoder.encode(v, "UTF-8"))
        }
        return sb.toString()
    }

    private suspend fun firstSite(): JSONObject? {
        val spider = fetchTvBoxConfig() ?: return null
        val sites = spider.optJSONArray("sites") ?: return null
        return if (sites.length() > 0) sites.getJSONObject(0) else null
    }

    private suspend fun fetchTvBoxConfig(): JSONObject? =
        try {
            JSONObject(SourceBootstrap.defaultTvBoxJson())
        } catch (e: Exception) {
            null
        }

    private suspend fun get(url: String): String {
        if (url.isBlank()) return ""
        // 内置演示源兜底
        if (url.contains("example.com")) {
            return if (url.contains("ac=detail") || url.contains("ac=videoplay")) {
                SourceBootstrap.demoVideoDetailBody()
            } else {
                SourceBootstrap.demoVideoSearchBody()
            }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().use { it.body?.string() ?: "" }
            }.getOrDefault("")
        }
    }
}
