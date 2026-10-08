package com.aggregator.shell.core.search

import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 聚合搜索服务。
 *
 * 引擎（[VideoEngine]/[ReaderEngine]）内部已遍历所有已启用源并合并返回，
 * 这里在引擎层之上做：① 记录搜索历史；② 跨源去重；③ 统一结果模型。
 * 动态选源仍由底层 Repositories/引擎承担，本类只接引擎能力接口与历史记录器，
 * 不直接碰 Room，保持 core:search 对数据层的轻量依赖。
 */
data class AggSearchResult(
    val items: List<AggSearchItem>,
    val matchedSources: Int,
    val total: Int
)

data class AggSearchItem(
    val title: String,
    val year: String = "",
    val coverUrl: String = "",
    val type: String = "",
    val sourceKey: String,
    val contentId: String,
    val module: String
)

class SearchAggregator(
    private val videoEngine: VideoEngine,
    private val searchHistoryRecorder: suspend (module: String, keyword: String) -> Unit
) {

    suspend fun searchVideos(keyword: String): AggSearchResult =
        withContext(Dispatchers.IO) {
            searchHistoryRecorder("VIDEO", keyword)
            val raw = runCatching { videoEngine.search(keyword, 1) }.getOrDefault(emptyList())
            val deduped = dedup(raw)
            AggSearchResult(
                items = deduped.map {
                    AggSearchItem(
                        title = it.title,
                        year = it.year,
                        coverUrl = it.coverUrl,
                        type = it.type,
                        sourceKey = it.sourceKey,
                        contentId = it.id,
                        module = "VIDEO"
                    )
                },
                matchedSources = deduped.map { it.sourceKey }.distinct().size,
                total = deduped.size
            )
        }

    private fun dedup(results: List<VideoResult>): List<VideoResult> {
        val seen = LinkedHashSet<String>()
        return results.filter { r ->
            seen.add("${r.title.trim().lowercase()}|${r.year.trim()}|${r.sourceKey}")
        }
    }
}
