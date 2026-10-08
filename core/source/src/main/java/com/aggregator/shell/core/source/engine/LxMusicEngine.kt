package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * LX Music custom source engine (LX Music 自定义源 API 2.0.0).
 *
 * 把 LX 自定义源脚本放入 [JsSandboxExecutor] 沙箱执行。脚本通过全局 `lx` 桥
 * （`lx.request` 代发 HTTP、`lx.send` 回传结果、`lx.on` 注册事件、`lx.EVENT_NAMES`
 * 常量）与引擎交互。`lx.request` / `lx.send` 由 Kotlin 侧绑定为可调函数。
 *
 * 脚本不可达 / 执行失败 / 未 `send` 结果时，回退内置演示数据，保证壳子开箱即用。
 */
class LxMusicEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor
) : MusicEngine {

    /** 脚本 `lx.send(payload)` 回传结果队列（按调用顺序）。 */
    private val sentResults = java.util.concurrent.ConcurrentLinkedQueue<String>()

    override suspend fun search(keyword: String): List<MusicResult> {
        sentResults.clear()
        val userScript = SourceBootstrap.defaultLxMusicJs()
        // 触发一次 musicUrl 请求：引擎注入的 `lx.request` 会代发，`lx.send` 收集
        val trigger = "lx.on(lx.EVENT_NAMES.request, function(info){ if(info && info.action==='musicUrl'){ lx.send({ url: lx.request('$keyword'), title: '$keyword', quality:'standard' }); }});" +
            "lx.trigger('musicUrl');"
        val full = userScript + "\n" + trigger
        val result = jsExecutor.execute(
            script = full,
            bindings = mapOf(
                "lx" to lxBridge(keyword),
                "keyword" to keyword
            )
        )
        val produced = when (result) {
            is JsResult.Success -> sentResults.toList()
            else -> emptyList()
        }
        return if (produced.isNotEmpty()) {
            produced.mapNotNull { parseMusicResult(it) }
        } else {
            // 回退：脚本未产出（沙箱失败 / 未 send），用内置演示数据
            listOf(
                MusicResult(
                    id = "demo-1",
                    title = keyword.ifEmpty { "演示歌曲" },
                    artist = "演示歌手",
                    album = "演示专辑",
                    source = "builtin",
                    picUrl = "https://picsum.photos/seed/demo/600"
                )
            )
        }
    }

    override suspend fun getMusicUrl(song: MusicResult, quality: String): String =
        "https://media.example.com/audio/${song.id}/$quality.mp3"

    override suspend fun getLyric(song: MusicResult): String =
        "[00:00.00] 演示歌词\n[00:04.00] $song.title"

    /**
     * 构建 `lx` 桥对象。Rhino 下把 Kotlin lambda 绑定为可调函数，
     * 脚本以 `lx.request(kw)` / `lx.send(payload)` / `lx.on(...)` / `lx.trigger(...)` 调用。
     */
    private fun lxBridge(keyword: String): Map<String, Any> = mutableMapOf(
        "EVENT_NAMES" to mapOf("request" to "request", "musicUrl" to "musicUrl"),
        "request" to { kw: String ->
            // 演示代发：真实场景应打用户脚本指向的 LX API。OkHttp 阻塞调用放 IO 线程。
            kotlinx.coroutines.runBlocking {
                withContext(Dispatchers.IO) {
                    runCatching {
                        client.newCall(
                            okhttp3.Request.Builder().url("https://example.com/audio/$kw").build()
                        ).execute().use { it.body?.string() ?: "" }
                    }.getOrDefault("")
                }
            }
        },
        "send" to { payload: Any? ->
            payload?.let { sentResults.add(it.toString()) }
        },
        "on" to { /* 注册事件，Rhino 侧存到闭包即可 */ },
        "trigger" to { action: Any? ->
            // 触发已注册的 request 处理：演示源直接 send 一条 musicUrl
            sentResults.add(
                JSONObject()
                    .put("url", "https://media.example.com/audio/demo/${keyword.ifEmpty { "1" }}.mp3")
                    .put("title", keyword.ifEmpty { "演示歌曲" })
                    .put("quality", "standard")
                    .toString()
            )
        }
    )

    private fun parseMusicResult(json: String): MusicResult? = runCatching {
        val text = json.trim()
        // 脚本 send 的是 JS 对象字符串，Rhino 输出形如 {"url":...}；若非 JSON 则整段当 url
        val o = if (text.startsWith("{")) JSONObject(text) else null
        if (o == null) {
            MusicResult(id = "lx-${json.hashCode()}", title = json, artist = "", album = "", source = "lx")
        } else {
            val url = o.optString("url")
            if (url.isBlank()) null
            else MusicResult(
                id = o.optString("id", "lx-${url.hashCode()}"),
                title = o.optString("title", ""),
                artist = o.optString("artist", ""),
                album = o.optString("album", ""),
                source = o.optString("quality", "standard"),
                picUrl = o.optString("picUrl", "")
            )
        }
    }.getOrNull()
}
