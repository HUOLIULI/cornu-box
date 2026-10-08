package com.aggregator.shell.core.media.epg

import com.aggregator.shell.core.data.local.EpgDao
import com.aggregator.shell.core.data.local.entity.EpgChannelEntity
import com.aggregator.shell.core.data.local.entity.EpgProgramEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** 当前直播频道 + 其节目单 + 正在播节目。 */
data class EpgSnapshot(
    val channel: EpgChannel?,
    val nowPlaying: EpgProgram?,
    val upcoming: List<EpgProgram>
)

/**
 * EPG 节目单提供器。
 *
 * 优先走真实 XMLTV 源（[EpgParser.load]，明文或 gzip），拉取结果落 Room（[EpgDao]）
 * 持久化供离线/快速读取；真实源不可达或解析为空时回退内置演示 EPG，保证
 * IPTV 壳子开箱有节目单可看。
 */
@Singleton
class EpgProvider @Inject constructor(
    private val client: OkHttpClient,
    private val parser: EpgParser,
    private val epgDao: EpgDao
) {

    /**
     * 取指定直播频道的节目单。
     *
     * @param epgUrl 该频道对应的 XMLTV EPG 源；为空/拉取失败/解析无节目时回退演示 EPG。
     */
    suspend fun epgFor(epgUrl: String, channelId: String = "live-demo"): EpgSnapshot {
        val channels = if (epgUrl.isNotBlank()) {
            runCatching { parser.load(epgUrl) }.getOrNull()
        } else null
        val source = channels
            ?.firstOrNull { it.id == channelId }
            ?: channels?.firstOrNull()
            ?: demo()
        persist(source)
        return snapshotFor(source)
    }

    /** 全部演示 EPG 频道（离线兜底）。 */
    suspend fun allEpg(): List<EpgChannel> = listOf(demo())

    private fun snapshotFor(channel: EpgChannel): EpgSnapshot {
        val now = System.currentTimeMillis()
        val nowPlaying = channel.programs.firstOrNull { now in it.startTime..it.endTime }
        val upcoming = channel.programs.filter { it.startTime > now }.sortedBy { it.startTime }
        return EpgSnapshot(channel, nowPlaying, upcoming)
    }

    /** 落 Room：先清旧节目与频道，再写频道与节目，供离线/快速读取。 */
    private suspend fun persist(channel: EpgChannel) = withContext(Dispatchers.IO) {
        runCatching {
            epgDao.clearPrograms()
            epgDao.clearChannels()
            epgDao.upsertChannels(
                listOf(
                    EpgChannelEntity(
                        channelId = channel.id,
                        displayName = channel.displayName,
                        iconUrl = channel.iconUrl
                    )
                )
            )
            epgDao.upsertPrograms(
                channel.programs.map {
                    EpgProgramEntity(
                        id = "${it.channelId}-${it.startTime}",
                        channelId = it.channelId,
                        title = it.title,
                        startTime = it.startTime,
                        endTime = it.endTime,
                        description = it.description,
                        iconUrl = it.iconUrl
                    )
                }
            )
        }
    }

    /** 内置演示 EPG（相对当前时间生成，保证 UI 上有"正在播/即将播"）。 */
    private suspend fun demo(): EpgChannel {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        return EpgChannel(
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
    }
}
