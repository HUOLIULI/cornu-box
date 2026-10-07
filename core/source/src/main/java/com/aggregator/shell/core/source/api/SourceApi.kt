package com.aggregator.shell.core.source.api

/**
 * Unified capability result for source connectivity tests.
 */
data class SourceTestResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long = 0
)

/**
 * Capability-based engine interfaces. Each module uses the interface that
 * matches its domain so that a single generic SourceEngine<T> does not force
 * unrelated operations (e.g. chapters) onto music.
 */
interface ReaderEngine {
    suspend fun search(keyword: String, page: Int = 1): List<BookResult>
    suspend fun getToc(bookId: String): List<Chapter>
    suspend fun getContent(chapterId: String): String
}

interface VideoEngine {
    suspend fun search(keyword: String, page: Int = 1): List<VideoResult>
    suspend fun getDetail(id: String): VideoDetail
    suspend fun getPlayUrl(id: String, flag: String): PlayResult
}

interface MusicEngine {
    suspend fun search(keyword: String): List<MusicResult>
    suspend fun getMusicUrl(song: MusicResult, quality: String): String
    suspend fun getLyric(song: MusicResult): String
}

data class BookResult(
    val id: String,
    val name: String,
    val author: String,
    val coverUrl: String,
    val bookUrl: String,
    val lastChapter: String = "",
    val sourceName: String = ""
)

data class Chapter(
    val index: Int,
    val title: String,
    val url: String
)

data class VideoResult(
    val id: String,
    val title: String,
    val coverUrl: String,
    val type: String = "",
    val year: String = "",
    val sourceKey: String = ""
)

data class VideoDetail(
    val id: String,
    val title: String,
    val desc: String,
    val episodes: Map<String, List<String>>,
    val sourceKey: String = ""
)

data class PlayResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val name: String = ""
)

data class MusicResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val source: String,
    val picUrl: String = "",
    val durationMs: Long = 0
)
