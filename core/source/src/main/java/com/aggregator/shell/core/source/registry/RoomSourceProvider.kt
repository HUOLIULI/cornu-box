package com.aggregator.shell.core.source.registry

import com.aggregator.shell.core.data.local.BookSourceDao
import com.aggregator.shell.core.data.local.LiveSourceDao
import com.aggregator.shell.core.data.local.MusicSourceDao
import com.aggregator.shell.core.data.local.VideoSourceDao
import kotlinx.coroutines.flow.first

/**
 * 基于 Room 源表的 [SourceProvider] 实现：读用户订阅导入后的真实源。
 * 表为空时回退到 [FallbackSourceProvider]，保证壳子开箱即用。
 */
class RoomSourceProvider(
    private val videoSourceDao: VideoSourceDao,
    private val bookSourceDao: BookSourceDao,
    private val liveSourceDao: LiveSourceDao,
    private val musicSourceDao: MusicSourceDao,
    private val fallback: SourceProvider = FallbackSourceProvider()
) : SourceProvider {

    override suspend fun videoSources(): List<VideoSourceDef> {
        val rows = videoSourceDao.all().first().filter { it.enabled }
        if (rows.isEmpty()) return fallback.videoSources()
        return rows.map {
            VideoSourceDef(
                sourceId = it.sourceId,
                name = it.name,
                api = it.api,
                spider = it.spider,
                rawJson = it.rawJson,
                enabled = it.enabled
            )
        }
    }

    override suspend fun bookSources(): List<BookSourceDef> {
        val rows = bookSourceDao.all().first().filter { it.enabled }
        if (rows.isEmpty()) return fallback.bookSources()
        return rows.map {
            BookSourceDef(
                sourceId = it.sourceId,
                name = it.name,
                rawJson = it.rawJson,
                enabled = it.enabled
            )
        }
    }

    override suspend fun liveSources(): List<LiveSourceDef> {
        val rows = liveSourceDao.all().first().filter { it.enabled }
        if (rows.isEmpty()) return fallback.liveSources()
        return rows.map {
            LiveSourceDef(
                sourceId = it.sourceId,
                name = it.name,
                url = it.url,
                epg = it.epg,
                group = it.group,
                enabled = it.enabled
            )
        }
    }

    override suspend fun musicScripts(): List<MusicScriptDef> {
        val rows = musicSourceDao.all().first().filter { it.enabled }
        if (rows.isEmpty()) return fallback.musicScripts()
        return rows.map {
            MusicScriptDef(
                sourceId = it.sourceId,
                name = it.name,
                scriptPath = it.scriptPath,
                enabled = it.enabled,
                isBuiltin = it.isBuiltin
            )
        }
    }
}
