package com.aggregator.shell.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_logs")
data class SourceLogEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    val category: String,
    val url: String? = null,
    val method: String? = null,
    val status: Int = 0,
    val detail: String = "",
    val durationMs: Long = 0
)
