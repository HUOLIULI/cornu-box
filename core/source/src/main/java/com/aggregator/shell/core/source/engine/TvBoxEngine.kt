package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.PlayResult
import com.aggregator.shell.core.source.api.VideoDetail
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.registry.LiveSourceDef
import com.aggregator.shell.core.source.registry.SourceProvider
import com.aggregator.shell.core.source.registry.VideoSourceDef
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
 * 通过 [SourceProvider] 读取 Room 源表中的全部已启用源并并发聚合搜索；
 * 源表为空时由 [SourceProvider] 回退内置演示源（example.com），保证开箱有内容。
 */
class TvBoxEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor,
    private val pythonRuntime: PythonRuntime,
    private val sourceProvider: SourceProvider
) : VideoEngine {

    override suspend fun search(keyword: String, page: Int): List<VideoResult> {
        val defs = sourceProvider.videoSources().filter { it.enabled }
        return defs.mapNotNull { def ->
            runCatching {
                val api = def.api.ifBlank { def.rawJson.let { runCatching { JSONObject(it).optString("api") }.getOrDefault("") } }
                if (api.isBlank()) null
                val resp = get(buildUrl(api, "search", mapOf("wd" to keyword, "pg" to page.toString())), def.rawJson)
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
                        sourceKey = def.sourceId
                    )
                }
            }.getOrNull()
        }.flatten()
    }

    override suspend fun getDetail(id: String): VideoDetail {
        val def = sourceProvider.videoSources().firstOrNull { it.enabled } ?: return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val api = resolveApi(def)
        if (api.isBlank()) return VideoDetail(id = id, title = "", desc = "", episodes = emptyMap())
        val resp = get(buildUrl(api, "detail", mapOf("ids" to id)), def.rawJson)
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
            sourceKey = def.sourceId
        )
    }

    override suspend fun getPlayUrl(id: String, flag: String): PlayResult {
        val def = sourceProvider.videoSources().firstOrNull { it.enabled } ?: return PlayResult(url = "", name = "")
        val api = resolveApi(def)
        if (api.isBlank()) return PlayResult(url = "", name = "")
        val resp = get(buildUrl(api, "videoplay", mapOf("ids" to id)), def.rawJson)
        if (resp.isBlank()) return PlayResult(url = "", name = "")
        val json = JSONObject(resp)
        val vod = json.optJSONArray("list")?.optJSONObject(0)
            ?: json.optJSONObject("vod")
            ?: return PlayResult(url = "", name = "")
        val episodes = parseEpisodes(vod.optString("vod_play_url"))
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

    /** 直播源：从 Room 源表读取（用户导入的 IPTV 列表），空则回退内置演示。 */
    suspend fun liveChannels(): List<LiveSourceDef> = sourceProvider.liveSources()

    // ---------- 内部 ----------

    private fun resolveApi(def: VideoSourceDef): String {
        val direct = def.api
        if (direct.isNotBlank()) return direct
        // 兼容整段 TVBox spider JSON 导入：从 rawJson.sites[0].api 提取
        return runCatching {
            val spider = JSONObject(def.rawJson)
            val sites = spider.optJSONArray("sites") ?: return@runCatching ""
            sites.optJSONObject(0)?.optString("api").orEmpty()
        }.getOrDefault("")
    }

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

    private suspend fun get(url: String, rawJson: String = ""): String {
        if (url.isBlank()) return ""
        // 内置演示源兜底（回退源）
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
