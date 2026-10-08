package com.aggregator.shell.core.media.lyric

/** 单条 LRC 歌词行：[timeMs] 为该行出现的时间（毫秒）。 */
data class LyricLine(val timeMs: Long, val text: String)

/** 解析后的歌词时间轴。 */
data class ParsedLyric(
    val lines: List<LyricLine>,
    val durationMs: Long
)

/**
 * LRC（时间轴歌词）解析器。
 *
 * 支持标准 `mm:ss.xx` / `mm:ss` 与 `[tag]` 元数据行；同一时间戳可重复。
 * 用于音乐播放页逐行同步高亮当前歌词。
 */
object LrcParser {

    private val TIME_RE = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\](.*)""")
    private val TAG_RE = Regex("""\[(\w+):[^\]]*]""")

    fun parse(lrc: String): ParsedLyric {
        val raw = ArrayList<LyricLine>()
        var duration = 0L
        lrc.lineSequence().forEach { line ->
            val s = TAG_RE.replace(line) { "" }.trim()
            TIME_RE.findAll(s).forEach { m ->
                val mm = m.groupValues[1].toLongOrNull() ?: return@forEach
                val ss = m.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = m.groupValues[3]
                val frac = fracStr.toLongOrNull() ?: 0L
                val timeMs = mm * 60_000 + ss * 1000 + when (fracStr.length) {
                    1 -> frac * 100
                    2 -> frac
                    else -> frac / 10
                }
                val text = m.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    raw.add(LyricLine(timeMs, text))
                    duration = maxOf(duration, timeMs)
                }
            }
        }
        return ParsedLyric(raw.sortedBy { it.timeMs }, duration)
    }

    /** 取 [positionMs] 处正在显示的那一行。 */
    fun lineAt(lyric: ParsedLyric, positionMs: Long): Int {
        if (lyric.lines.isEmpty()) return -1
        var idx = 0
        for (i in lyric.lines.indices) {
            if (lyric.lines[i].timeMs <= positionMs) idx = i else return idx
        }
        return idx
    }
}
