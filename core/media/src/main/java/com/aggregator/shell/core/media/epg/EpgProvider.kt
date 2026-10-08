package com.aggregator.shell.core.media.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPG 节目单提供器。默认走内置演示 EPG（与 [SourceBootstrap] 演示直播频道对应），
 * 真实 EPG（XMLTV 源 + [EpgParser] 拉取 + 落 Room）后续替换 [loadDemo] 即可，
 * 接口 [epgFor] 不变。
 */
@Singleton
class EpgProvider @Inject constructor(
    private val client: OkHttpClient,
    private val parser: EpgParser
) {

    /** 取指定直播频道的节目单；无 EPG 源时返回空。演示实现：单演示频道。 */
    suspend fun epgFor(channelId: String): List<EpgProgram> =
        loadDemo().firstOrNull()?.programs ?: emptyList()

    /** 全部演示 EPG 频道。 */
    suspend fun allEpg(): List<EpgChannel> = loadDemo()

    /**
     * 演示 EPG：内置两条频道节目单（时间戳相对当前时间生成，保证 UI 上"正在播/即将播"有数据）。
     * 真实实现：`parser.load(xmltvUrl)`。
     */
    private suspend fun loadDemo(): List<EpgChannel> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        listOf(
            EpgChannel(
                id = "live-demo",
                displayName = "演示直播",
                iconUrl = null,
                programs = listOf(
                    EpgProgram("live-demo", "早间新闻", now - 2 * hour, now - hour, "今日要闻速览", null),
                    EpgProgram("live-demo", "午间剧场", now - hour, now + hour, "经典剧集回放", null),
                    EpgProgram("live-demo", "晚间综艺", now + hour, now + 3 * hour, "明星互动环节", null),
                    EpgProgram("live-demo", "深夜档", now + 3 * hour, now + 5 * hour, "深夜治愈", null)
                )
            )
        )
    }
}
