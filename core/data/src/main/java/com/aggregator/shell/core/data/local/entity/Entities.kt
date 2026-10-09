package com.aggregator.shell.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "book_sources")
data class BookSourceEntity(
    @PrimaryKey val sourceId: String,
    val name: String,
    val group: String = "",
    val url: String,
    val enabled: Boolean = true,
    val rawJson: String,
    val sortOrder: Int = 0,
    val lastUpdate: Long = 0L,
    val lastTestResult: String? = null
)

@Entity(tableName = "bookshelf")
data class BookshelfEntity(
    @PrimaryKey val bookId: String,
    val sourceId: String,
    val name: String,
    val author: String,
    val coverUrl: String,
    val lastChapter: String = "",
    val lastReadTime: Long = 0L,
    val readProgress: Float = 0f
)

@Entity(tableName = "video_sources")
data class VideoSourceEntity(
    @PrimaryKey val sourceId: String,
    val name: String,
    val api: String,
    val spider: String = "",
    val ext: String = "",
    val enabled: Boolean = true,
    val rawJson: String = "{}",
    val lastUpdate: Long = 0L
)

@Entity(tableName = "live_sources")
data class LiveSourceEntity(
    @PrimaryKey val sourceId: String,
    val name: String,
    val url: String,
    val epg: String = "",
    val group: String = "",
    val enabled: Boolean = true,
    val lastUpdate: Long = 0L
)

@Entity(tableName = "music_sources")
data class MusicSourceEntity(
    @PrimaryKey val sourceId: String,
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val scriptPath: String,
    val remoteUrl: String? = null,
    val enabled: Boolean = true,
    val isBuiltin: Boolean = false
)

@Entity(tableName = "epg_channels")
data class EpgChannelEntity(
    @PrimaryKey val channelId: String,
    val displayName: String,
    val iconUrl: String? = null
)

@Entity(tableName = "epg_programs")
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String? = null,
    val iconUrl: String? = null
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val contentId: String,
    val title: String,
    val positionMs: Long = 0L,
    val module: String,
    val updated: Long = 0L
)

@Entity(tableName = "favorites")
data class FavoritesEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val contentId: String,
    val title: String,
    val coverUrl: String? = null,
    val module: String,
    val addedTime: Long = 0L,
    val category: String = "default"
)

@Entity(tableName = "search_history", primaryKeys = ["query", "module"])
data class SearchHistoryEntity(
    val query: String,
    val module: String,
    val lastUsed: Long = 0L
)
