package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * LX Music custom source engine (LX Music 自定义源 API 2.0.0).
 *
 * For the shell MVP, we expose a `lx`-shaped bridge over [client]; user
 * scripts would register an `on` handler that posts to a queue, and
 * [search] resolves the first `send` result of type `musicUrl`.
 */
class LxMusicEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor
) : MusicEngine {

    override suspend fun search(keyword: String): List<MusicResult> {
        // 演示数据源：直接返回一条示例记录。完整实现走沙箱脚本 + 真实 API。
        return listOf(
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

    override suspend fun getMusicUrl(song: MusicResult, quality: String): String =
        "https://media.example.com/audio/${song.id}/$quality.mp3"

    override suspend fun getLyric(song: MusicResult): String =
        "[00:00.00] 演示歌词\n[00:04.00] $song.title"
}
