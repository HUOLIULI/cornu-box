package com.aggregator.shell.core.media.danmaku

import android.content.Context
import com.aggregator.shell.core.common.LlmConfigKeys
import com.aggregator.shell.core.data.di.appDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject

/**
 * 配置化真实弹幕源：从用户自备的弹幕 API 拉取剧集弹幕。
 *
 * 弹幕 API 端点与鉴权由用户在「设置 → 弹幕源」配置（仅保存在本机 DataStore，
 * 应用不内置/不读取任何平台凭据）：
 * - `danmaku_base_url`：弹幕 API 根地址（如 `https://api.bilibili.com`）
 * - `danmaku_api_key`：鉴权 token（可选）
 *
 * 约定接口形态（兼容 DanDanPlay 风格）：
 * - 查集：`GET {base}/api/av1/danmaku?search={title}` → `[{"epsid": "...", "episode": N, ...}]`
 * - 取弹幕：`GET {base}/api/av1/danmaku/{epsid}` → `[{"time": 1.5, "text": "...", "color": ...}]`
 *
 * 端点未配置、拉取失败或无结果时，回退 [LocalDanmakuSource] 演示弹幕，保证开箱可用。
 */
class RemoteDanmakuSource @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext
    private val context: Context
) : DanmakuSource {

    private val fallback = LocalDanmakuSource()

    override suspend fun searchEpisode(title: String, episode: Int): String? {
        val baseUrl = configBaseUrl() ?: return fallback.searchEpisode(title, episode)
        val apiKey = configApiKey()
        val url = StringBuilder(baseUrl.trimEnd('/'))
            .append("/api/av1/danmaku?search=")
            .append(URLEncoder.encode(title, "UTF-8"))
            .append("&episode=").append(episode)
            .toString()
        val body = get(url, apiKey) ?: return fallback.searchEpisode(title, episode)
        return runCatching {
            val root = JSONObject(body)
            val arr = root.optJSONArray("result")
                ?: root.optJSONObject("result")?.optJSONArray("list")
            if (arr != null && arr.length() > 0) arr.getJSONObject(0).optString("epsid").takeIf { it.isNotBlank() }
            else null
        }.getOrNull() ?: fallback.searchEpisode(title, episode)
    }

    override suspend fun loadDanmaku(episodeId: String): List<DanmakuItem> {
        val baseUrl = configBaseUrl() ?: return fallback.loadDanmaku(episodeId)
        val apiKey = configApiKey()
        val url = "${baseUrl.trimEnd('/')}/api/av1/danmaku/$episodeId"
        val body = get(url, apiKey) ?: return fallback.loadDanmaku(episodeId)
        val items = runCatching {
            val root = JSONObject(body)
            val arr = root.optJSONArray("result")
                ?: root.optJSONObject("result")?.optJSONArray("list")
            val out = mutableListOf<DanmakuItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val text = o.optString("text")
                    if (text.isBlank()) continue
                    val timeSec = o.optDouble("time", o.optDouble("t", 0.0))
                    val color = o.optLong("color", 0xFFFFFFFFL).toInt()
                    out += DanmakuItem(
                        timeMs = (timeSec * 1000).toLong(),
                        text = text,
                        color = color
                    )
                }
            }
            out
        }.getOrDefault(emptyList())
        return if (items.isNotEmpty()) items else fallback.loadDanmaku(episodeId)
    }

    private suspend fun get(url: String, apiKey: String?): String? =
        runCatching {
            val builder = okhttp3.Request.Builder().url(url)
            if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()
            }
        }.getOrNull()

    private suspend fun configBaseUrl(): String? {
        val prefs = context.appDataStore.data.first()
        return prefs[LlmConfigKeys.DANMAKU_BASE_URL]?.takeIf { it.isNotBlank() }
    }

    private suspend fun configApiKey(): String? {
        val prefs = context.appDataStore.data.first()
        return prefs[LlmConfigKeys.DANMAKU_API_KEY]?.takeIf { it.isNotBlank() }
    }
}
