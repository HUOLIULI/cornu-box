package com.aggregator.shell.core.source.registry

import com.aggregator.shell.core.source.engine.SourceBootstrap
import kotlinx.coroutines.flow.first

/**
 * 源登记表：把 Room 源表（video/book/music）查询结果喂给引擎。
 *
 * 三个引擎各自只读自己模块的源；Room 表为空时回退到 [SourceBootstrap]
 * 内置演示 JSON，保证壳子开箱即用。订阅导入后 `Repositories` 写 Room，
 * 引擎下次调用即可取到真实源配置，形成"导入 → 生效"闭环。
 */
interface SourceProvider {

    /** 全部已启用影视源（含 api/spider/rawJson），空则回退演示。 */
    suspend fun videoSources(): List<VideoSourceDef>

    /** 全部已启用书源（rawJson 为完整 Legado 源 JSON），空则回退演示。 */
    suspend fun bookSources(): List<BookSourceDef>

    /** 全部已启用直播源。 */
    suspend fun liveSources(): List<LiveSourceDef>

    /** 全部已启用音乐源脚本，空则回退内置脚本。 */
    suspend fun musicScripts(): List<MusicScriptDef>
}

data class VideoSourceDef(
    val sourceId: String,
    val name: String,
    val api: String,
    val spider: String,
    val rawJson: String,
    val enabled: Boolean
)

data class BookSourceDef(
    val sourceId: String,
    val name: String,
    val rawJson: String,
    val enabled: Boolean
)

data class LiveSourceDef(
    val sourceId: String,
    val name: String,
    val url: String,
    val epg: String,
    val group: String,
    val enabled: Boolean
)

data class MusicScriptDef(
    val sourceId: String,
    val name: String,
    val scriptPath: String,
    val enabled: Boolean,
    val isBuiltin: Boolean
)

/** 仅用于测试 / 单测注入的回退提供者。 */
class FallbackSourceProvider : SourceProvider {
    override suspend fun videoSources(): List<VideoSourceDef> =
        listOf(
            VideoSourceDef(
                sourceId = "builtin-video",
                name = "内置演示影视源",
                api = "",
                spider = "",
                rawJson = SourceBootstrap.defaultTvBoxJson(),
                enabled = true
            )
        )

    override suspend fun bookSources(): List<BookSourceDef> =
        listOf(
            BookSourceDef(
                sourceId = "builtin-book",
                name = "内置演示书源",
                rawJson = SourceBootstrap.defaultLegadoSourceJson(),
                enabled = true
            )
        )

    override suspend fun liveSources(): List<LiveSourceDef> =
        listOf(
            LiveSourceDef(
                sourceId = "builtin-live",
                name = "内置演示直播",
                url = "https://example.com/live/demo.m3u8",
                epg = "",
                group = "演示",
                enabled = true
            )
        )

    override suspend fun musicScripts(): List<MusicScriptDef> =
        listOf(
            MusicScriptDef(
                sourceId = "builtin-music",
                name = "内置演示音乐源",
                scriptPath = SourceBootstrap.defaultLxMusicJs(),
                enabled = true,
                isBuiltin = true
            )
        )
}
