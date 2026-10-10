package com.aggregator.shell.core.source.search

import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 多源聚合搜索器：并发查询所有已启用的 [VideoEngine]，合并结果并按
 * (title, sourceKey) 去重，同一影片来自不同源的结果各保留一条，
 * 供 UI 展示「换源」。
 */
data class AggregateVideoResult(
    val result: VideoResult,
    val sourceKeys: List<String>
)

class AggregateVideoSearch(
    private val engines: List<VideoEngine>
) {

    suspend fun search(keyword: String, page: Int = 1): List<AggregateVideoResult> {
        if (engines.isEmpty()) return emptyList()
        val kw = keyword.trim().lowercase()
        val lists = coroutineScope {
            engines.map { engine ->
                async {
                    runCatching { engine.search(keyword, page) }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        // 按 (title, sourceKey) 去重：同一影片不同来源各保留一条
        val deduped = LinkedHashSet<Pair<String, String>>()
        val items = ArrayList<VideoResult>()
        for (list in lists) {
            for (item in list) {
                val key = item.title.trim().lowercase()
                if (key.isEmpty()) continue
                val sourceKey = item.sourceKey.ifBlank { "default" }
                if (deduped.add(key to sourceKey)) {
                    items.add(item)
                }
            }
        }
        // 同一影片的来源集合（供换源），按影片聚合
        val sourceKeysByTitle = LinkedHashMap<String, MutableList<String>>()
        for (item in items) {
            val key = item.title.trim().lowercase()
            val sk = item.sourceKey.ifBlank { "default" }
            sourceKeysByTitle.getOrPut(key) { mutableListOf() }
                .add(sk)
        }
        // 相关性排序：标题含关键词优先，其次命中源数量多的优先
        return items.map { item ->
            val key = item.title.trim().lowercase()
            AggregateVideoResult(item, sourceKeysByTitle[key] ?: emptyList())
        }.sortedWith(
            if (kw.isEmpty()) {
                compareByDescending<AggregateVideoResult> { it.sourceKeys.size }
            } else {
                compareByDescending<AggregateVideoResult> { it.result.title.lowercase().contains(kw) }
                    .thenByDescending { it.sourceKeys.size }
            }
        )
    }
}
