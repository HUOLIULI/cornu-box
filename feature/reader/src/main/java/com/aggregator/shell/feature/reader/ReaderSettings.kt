package com.aggregator.shell.feature.reader

/**
 * 阅读排版设置（聚合自 Legado 成熟排版：章节标题独立字体、行距 -2.0~+3.0、
 * 目录按书展开/折叠记忆、书架进度条三档）。
 *
 * 纯数据模型，由 reader UI 层持有并写入 DataStore；不改既有 Activity 结构。
 */
data class ReaderSettings(
    val bodyFontSizeSp: Int = 18,
    val chapterFontSizeSp: Int = 20,       // 章节标题独立字号（默认跟随正文，可覆盖）
    val followBodyForChapter: Boolean = true,
    val lineSpacing: Float = 0.0f,         // -2.0 .. +3.0
    val paragraphIndent: Boolean = true,
    val progressBarStyle: ProgressBarStyle = ProgressBarStyle.STANDARD,
    val expandedTocVolumes: Set<String> = emptySet()  // 按书记忆目录卷展开
) {
    enum class ProgressBarStyle { HIDDEN, STANDARD, ENHANCED }

    /** 目录是否展开某卷（按书 id + 卷标题记忆）。 */
    fun isTocExpanded(bookId: String, volume: String): Boolean =
        ("$bookId::$volume") in expandedTocVolumes

    fun withTocExpanded(bookId: String, volume: String, expanded: Boolean): ReaderSettings {
        val key = "$bookId::$volume"
        val next = if (expanded) expandedTocVolumes + key else expandedTocVolumes - key
        return copy(expandedTocVolumes = next)
    }

    fun resolvedChapterFontSp(): Int =
        if (followBodyForChapter) bodyFontSizeSp else chapterFontSizeSp

    companion object {
        const val LINE_MIN = -2.0f
        const val LINE_MAX = 3.0f
    }
}
