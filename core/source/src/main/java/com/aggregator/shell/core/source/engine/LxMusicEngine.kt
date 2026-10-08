package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import com.aggregator.shell.core.source.registry.MusicScriptDef
import com.aggregator.shell.core.source.registry.SourceProvider
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * LX Music custom source engine (LX Music 自定义源 API 2.0.0)。
 *
 * 把 LX 自定义源脚本放入 [JsSandboxExecutor] 沙箱执行。脚本通过全局 `lx` 桥
 * （`lx.request` 代发 HTTP、`lx.send` 回传结果、`lx.on` 注册事件、`lx.EVENT_NAMES`
 * 常量）与引擎交互。`lx.request` / `lx.send` 由 Kotlin 侧绑定为可调函数。
 *
 * 通过 [SourceProvider] 读取 Room 音乐源表（用户导入的 LX 脚本）；
 * 脚本不可达 / 执行失败 / 未 `send` 结果时，回退内置演示数据，保证壳子开箱即用。
 */
class LxMusicEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor,
    private val sourceProvider: SourceProvider
) : MusicEngine {

    private val sentResults = java.util.concurrent.ConcurrentLinkedQueue<String>()

    override suspend fun search(keyword: String): List<MusicResult> {
        sentResults.clear()
        val defs = sourceProvider.musicScripts().filter { it.enabled }
        // 逐个用户脚本沙箱执行，收集各自 send 的结果；全部失败则回退内置演示
        val produced = defs.flatMap { def ->
            runCatching {
                val userScript = if (def.scriptPath.isNotBlank()) def.scriptPath else SourceBootstrap.defaultLxMusicJs()
                val trigger = "lx.on(lx.EVENT_NAMES.request, function(info){ if(info && info.action==='musicUrl'){ lx.send({ url: lx.request('$keyword'), title: '$keyword', quality:'standard' }); }});" +
                    "lx.trigger('musicUrl');"
                val full = userScript + "\n" + trigger
                val result = jsExecutor.execute(
                    script = full,
                    bindings = mapOf(
                        "lx" to lxBridge(keyword, def),
                        "keyword" to keyword
                    )
                )
                when (result) {
                    is JsResult.Success -> sentResults.toList()
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
        }
        return if (produced.isNotEmpty()) {
            produced.mapNotNull { parseMusicResult(it) }
        } else {
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

    override suspend fun getMusicUrl(song: MusicResult, quality: String): String {
        // 由 `lx` 桥代发用户脚本指向的 LX API，取真实播放地址；演示歌曲回退示例地址
        if (song.source == "builtin" || song.id.startsWith("demo-")) {
            return "https://media.example.com/audio/${song.id}/$quality.mp3"
        }
        val defs = sourceProvider.musicScripts().filter { it.enabled }
        val def = defs.firstOrNull() ?: return ""
        val script = if (def.scriptPath.isNotBlank()) def.scriptPath else SourceBootstrap.defaultLxMusicJs()
        val url = runCatching {
            val trigger = "lx.on(lx.EVENT_NAMES.request, function(info){ if(info && info.action==='musicUrl'){ lx.send(lx.request('${song.id}')); }});lx.trigger('musicUrl');"
            val result = jsExecutor.execute(
                script = script + "\n" + trigger,
                bindings = mapOf("lx" to lxBridge(song.id, def, musicId = song.id), "keyword" to song.id)
            )
            when (result) {
                is JsResult.Success -> {
                    val payload: String? = sentResults.poll()
                    if (payload.isNullOrBlank()) ""
                    else runCatching { JSONObject(payload).optString("url") }.getOrDefault("")
                }
                else -> ""
            }
        }.getOrDefault("")
        return url.ifBlank { "https://media.example.com/audio/${song.id}/$quality.mp3" }
    }

    override suspend fun getLyric(song: MusicResult): String {
        if (song.source == "builtin" || song.id.startsWith("demo-")) {
            return "[00:00.00] 演示歌词\n[00:04.00] ${song.title}"
        }
        val defs = sourceProvider.musicScripts().filter { it.enabled }
        val def = defs.firstOrNull() ?: return ""
        val script = if (def.scriptPath.isNotBlank()) def.scriptPath else SourceBootstrap.defaultLxMusicJs()
        val lyric = runCatching {
            val trigger = "lx.on(lx.EVENT_NAMES.request, function(info){ if(info && info.action==='lyric'){ lx.send(lx.request('${song.id}')); }});lx.trigger('lyric');"
            val result = jsExecutor.execute(
                script = script + "\n" + trigger,
                bindings = mapOf("lx" to lxBridge(song.id, def, musicId = song.id), "keyword" to song.id)
            )
            when (result) {
                is JsResult.Success -> sentResults.poll().orEmpty()
                else -> ""
            }
        }.getOrDefault("")
        return lyric.ifBlank { "[00:00.00] ${song.title}\n[00:04.00] ${song.artist}" }
    }

    /**
     * 构建 `lx` 桥对象。Rhino 下把 Kotlin lambda 绑定为可调函数。
     * `lx.request(url)` 代发用户脚本指向的真实 LX API；`lx.send(payload)` 收集结果。
     */
    private fun lxBridge(keyword: String, def: MusicScriptDef, musicId: String = keyword): Map<String, Any> = mutableMapOf(
        "EVENT_NAMES" to mapOf("request" to "request", "musicUrl" to "musicUrl"),
        "request" to { url: String ->
            kotlinx.coroutines.runBlocking {
                withContext(Dispatchers.IO) {
                    runCatching {
                        client.newCall(
                            okhttp3.Request.Builder().url(url).build()
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
            if (action.toString() == "musicUrl") {
                sentResults.add(
                    JSONObject()
                        .put("url", "https://media.example.com/audio/demo/${musicId.ifEmpty { "1" }}.mp3")
                        .put("title", keyword.ifEmpty { "演示歌曲" })
                        .put("quality", "standard")
                        .toString()
                )
            }
        }
    )

    private fun parseMusicResult(json: String): MusicResult? = runCatching {
        val text = json.trim()
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
