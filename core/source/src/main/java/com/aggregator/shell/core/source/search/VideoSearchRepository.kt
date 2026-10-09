package com.aggregator.shell.core.source.search

import com.aggregator.shell.core.source.api.VideoResult

/**
 * 多源聚合搜索入口：一次并发查询所有已启用视频引擎，结果合并去重后按相关性排序。
 * 由 Hilt 注入 [AggregateVideoSearch]，feature 模块只依赖本接口。
 */
interface VideoSearchRepository {
    suspend fun search(keyword: String, page: Int = 1): List<AggregateVideoResult>
}

class VideoSearchRepositoryImpl(
    private val aggregateSearch: AggregateVideoSearch
) : VideoSearchRepository {

    override suspend fun search(keyword: String, page: Int): List<AggregateVideoResult> =
        aggregateSearch.search(keyword, page)

    companion object {
        /** 展开为平铺 [VideoResult] 列表（供列表页展示，保留 sourceKey 支持换源）。 */
        fun toVideoResults(items: List<AggregateVideoResult>): List<VideoResult> = items.map { it.result }
    }
}
