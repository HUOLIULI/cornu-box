package com.aggregator.shell.core.media.danmaku

/**
 * A single danmaku comment. Rendered on top of the player surface.
 */
data class DanmakuItem(
    val timeMs: Long,
    val text: String,
    val color: Int = 0xFFFFFFFF.toInt(),
    val fontSize: Int = 16,
    val mode: Mode = Mode.ROLL
) {
    enum class Mode { ROLL, TOP, BOTTOM }
}

/**
 * Lightweight Compose-based danmaku engine. Replaces DanmakuFlameMaster to
 * keep the shell buildable without a legacy native dependency. Items are
 * fed by [DanmakuSource] implementations (e.g. DanDanPlay API).
 */
interface DanmakuSource {
    suspend fun searchEpisode(title: String, episode: Int): String?
    suspend fun loadDanmaku(episodeId: String): List<DanmakuItem>
}
