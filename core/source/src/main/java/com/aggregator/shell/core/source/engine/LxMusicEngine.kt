package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.common.AppLog
import com.aggregator.shell.core.common.NoOpLog
import com.aggregator.shell.core.data.local.MusicSourceDao
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * LX Music custom source engine (LX Music 自定义源 API 2.0.0).
 *
 * When a user has imported a music source (Room [musicSourceDao]), the engine
 * resolves its `scriptPath` / `remoteUrl` and dispatches an LX `request` event
 * through [jsExecutor]. The built-in demo source is the fallback so the shell
 * runs out of the box.
 */
class LxMusicEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor,
    private val musicSourceDao: MusicSourceDao? = null,
    private val log: AppLog = NoOpLog
) : MusicEngine {

    override suspend fun search(keyword: String): List<MusicResult> {
        val script = resolveScript() ?: run {
            log.w(TAG, "no music source script available")
            return demoResults(keyword)
        }
        val bindings = buildLxBindings(
            action = "search",
            info = mapOf("keyword" to keyword, "type" to "all")
        )
        val result = jsExecutor.execute(script, bindings, timeoutMillis = 15_000)
        return when (result) {
            is JsResult.Success -> parseMusicList(result.value)
            is JsResult.Failure -> {
                log.e(TAG, "music search script failed", result.error)
                demoResults(keyword)
            }
            is JsResult.Timeout -> {
                log.w(TAG, "music search script timed out")
                demoResults(keyword)
            }
        }
    }

    override suspend fun getMusicUrl(song: MusicResult, quality: String): String {
        val script = resolveScript() ?: return demoMusicUrl(song, quality)
        val bindings = buildLxBindings(
            action = "musicUrl",
            info = song.toLxInfo().plus(mapOf("quality" to quality))
        )
        val result = jsExecutor.execute(script, bindings, timeoutMillis = 15_000)
        return when (result) {
            is JsResult.Success -> runCatching {
                org.json.JSONObject(result.value).optString("url")
            }.getOrDefault(demoMusicUrl(song, quality))
            is JsResult.Failure -> {
                log.e(TAG, "getMusicUrl script failed", result.error)
                demoMusicUrl(song, quality)
            }
            is JsResult.Timeout -> demoMusicUrl(song, quality)
        }
    }

    override suspend fun getLyric(song: MusicResult): String {
        val script = resolveScript()
            ?: return "[00:00.00] ${song.title}\n[00:04.00] ${song.artist}"
        val bindings = buildLxBindings(
            action = "lyric",
            info = song.toLxInfo()
        )
        val result = jsExecutor.execute(script, bindings, timeoutMillis = 15_000)
        return when (result) {
            is JsResult.Success -> result.value
            is JsResult.Failure -> {
                log.e(TAG, "getLyric script failed", result.error)
                "[00:00.00] ${song.title}\n[00:04.00] ${song.artist}"
            }
            is JsResult.Timeout -> "[00:00.00] ${song.title}\n[00:04.00] ${song.artist}"
        }
    }

    private fun demoResults(keyword: String): List<MusicResult> = listOf(
        MusicResult(
            id = "demo-1",
            title = keyword.ifEmpty { "演示歌曲" },
            artist = "演示歌手",
            album = "演示专辑",
            source = "builtin",
            picUrl = "https://picsum.photos/seed/demo/600"
        )
    )

    private fun demoMusicUrl(song: MusicResult, quality: String) =
        "https://media.example.com/audio/${song.id}/$quality.mp3"

    private fun parseMusicList(json: String): List<MusicResult> {
        val parsed = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = parsed.optJSONArray("list") ?: return emptyList()
        val out = mutableListOf<MusicResult>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            out.add(
                MusicResult(
                    id = m.optString("id"),
                    title = m.optString("title"),
                    artist = m.optString("artist"),
                    album = m.optString("album"),
                    source = m.optString("source"),
                    picUrl = m.optString("pic"),
                    durationMs = m.optLong("duration")
                )
            )
        }
        return out
    }

    /**
     * Build the LX `globalThis.lx` shape the user scripts expect: `on` / `send`
     * handlers plus the action payload.
     */
    private fun buildLxBindings(action: String, info: Map<String, Any>): Map<String, Any> = mapOf(
        "action" to action,
        "info" to info,
        "lxVersion" to "2.0.0"
    )

    private fun MusicResult.toLxInfo() = mapOf(
        "id" to id,
        "title" to title,
        "artist" to artist,
        "album" to album,
        "source" to source
    )

    /**
     * Resolve the active music source script. Prefer the first enabled source in
     * Room whose `scriptPath` is non-blank; fall back to the built-in demo.
     */
    private suspend fun resolveScript(): String? {
        musicSourceDao?.let { dao ->
            val rows = runCatching { dao.all().first() }.getOrDefault(emptyList())
            val pick = rows.firstOrNull { it.enabled && it.scriptPath.isNotBlank() }
            if (pick != null) {
                log.i(TAG, "using Room music source: ${pick.name}")
                return if (pick.remoteUrl != null) {
                    runCatching {
                        client.newCall(
                            okhttp3.Request.Builder().url(pick.remoteUrl!!).build()
                        ).execute().use { it.body?.string() ?: "" }
                    }.getOrNull()
                } else {
                    pick.scriptPath
                }
            }
        }
        log.i(TAG, "falling back to built-in demo music script")
        return runCatching { SourceBootstrap.defaultLxMusicJs() }.getOrNull()
    }

    companion object {
        const val TAG = "LxMusicEngine"
    }
}
