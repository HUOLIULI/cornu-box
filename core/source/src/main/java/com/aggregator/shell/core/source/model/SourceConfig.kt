package com.aggregator.shell.core.source.model

/**
 * Base shape for all user-managed source configurations.
 */
interface SourceConfig {
    val id: String
    val name: String
    val module: com.aggregator.shell.core.common.ModuleType
}

/**
 * TVBox / CatVod / T4 site descriptor, parsed from the `sites` block of a
 * TVBox config JSON.
 */
data class VideoSourceConfig(
    override val id: String,
    override val name: String,
    val api: String,
    val ext: String = "",
    val spider: String = "",
    val searchUrl: String = "",
    val detailUrl: String = "",
    override val module: com.aggregator.shell.core.common.ModuleType =
        com.aggregator.shell.core.common.ModuleType.VIDEO
) : SourceConfig

/**
 * IPTV live playlist entry (a `lives` array element).
 */
data class LiveSourceConfig(
    override val id: String,
    override val name: String,
    val url: String,
    val epg: String = "",
    val group: String = "",
    override val module: com.aggregator.shell.core.common.ModuleType =
        com.aggregator.shell.core.common.ModuleType.LIVE
) : SourceConfig

/**
 * Legado book source, persisted as raw JSON and parsed lazily by the engine.
 */
data class BookSourceConfig(
    override val id: String,
    override val name: String,
    val bookSourceUrl: String,
    val rawJson: String,
    override val module: com.aggregator.shell.core.common.ModuleType =
        com.aggregator.shell.core.common.ModuleType.READER
) : SourceConfig

/**
 * LX Music custom source descriptor.
 */
data class MusicSourceConfig(
    override val id: String,
    override val name: String,
    val script: String,
    val sourceKey: String = "",
    override val module: com.aggregator.shell.core.common.ModuleType =
        com.aggregator.shell.core.common.ModuleType.MUSIC
) : SourceConfig
