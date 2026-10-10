package com.aggregator.shell.core.media.episode

/**
 * 选集面板模型与集号解析（聚合自 PeekPro 选集面板：短显/列表/网格三布局、
 * 从文件名自动提取 S01E01/EP01 集号、正序倒序）。
 */
data class EpisodeItem(
    val index: Int,          // 0 基序号
    val title: String,        // 原始标题（如 "S01E03 暗夜追踪"）
    val number: Int?,        // 解析出的集号（1 基），无法解析为 null
    val url: String
)

enum class EpisodeLayout { SHORT, LIST, GRID }

object EpisodeIndexer {

    private val patterns = listOf(
        Regex("""[Ss](\d{1,2})\s*[Ee](\d{1,3})"""),   // S01E03
        Regex("""[Ee][Pp]\s*0*(\d{1,3})"""),          // EP03 / EP3
        Regex("""第\s*0*(\d{1,4})\s*[集话話回]"""),      // 第12集
        Regex("""^0*(\d{1,4})\s*$""")                 // 纯数字
    )

    /** 从标题文本解析 1 基集号；解析失败返回 null。 */
    fun parseNumber(title: String): Int? {
        for (p in patterns) {
            val m = p.find(title) ?: continue
            val g = m.groupValues
            // SxxExx 取最后一组（集号），其他取第一组
            val n = if (p.pattern.contains("S") && p.pattern.contains("E")) g[2].toInt()
                    else g[1].toInt()
            if (n in 1..9999) return n
        }
        return null
    }

    /** 把一条 vod_play_url 线路的 url#url 列表转成 EpisodeItem。 */
    fun build(titles: List<String>, urls: List<String>): List<EpisodeItem> {
        val count = minOf(titles.size, urls.size)
        return (0 until count).map { i ->
            EpisodeItem(
                index = i,
                title = titles[i],
                number = parseNumber(titles[i]),
                url = urls[i]
            )
        }
    }

    fun reorder(items: List<EpisodeItem>, ascending: Boolean): List<EpisodeItem> =
        if (ascending) items else items.reversed()
}
