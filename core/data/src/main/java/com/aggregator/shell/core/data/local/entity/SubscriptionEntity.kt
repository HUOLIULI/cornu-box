package com.aggregator.shell.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Unified source-subscription record used by every module. The concrete
 * source row (book_sources / video_sources / live_sources / music_sources)
 * is linked by `sourceId` and `module`.
 */
@Entity(tableName = "source_subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val subId: String,
    val name: String,
    @ColumnInfo(name = "module_type") val moduleType: String,
    val url: String,
    val updateInterval: Long = 86_400_000L,
    val lastUpdate: Long = 0L,
    val autoUpdate: Boolean = true,
    val sourceCount: Int = 0,
    val rawJson: String = ""
)
