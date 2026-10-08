package com.aggregator.shell.core.data.local

import androidx.room.*
import com.aggregator.shell.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookSourceDao {
    @Query("SELECT * FROM book_sources ORDER BY sortOrder")
    fun all(): Flow<List<BookSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: BookSourceEntity)

    @Query("DELETE FROM book_sources")
    suspend fun clearAll()
}

@Dao
interface BookshelfDao {
    @Query("SELECT * FROM bookshelf ORDER BY lastReadTime DESC")
    fun all(): Flow<List<BookshelfEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BookshelfEntity)

    @Query("DELETE FROM bookshelf WHERE bookId = :id")
    suspend fun remove(id: String)
}

@Dao
interface VideoSourceDao {
    @Query("SELECT * FROM video_sources")
    fun all(): Flow<List<VideoSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: VideoSourceEntity)

    @Query("DELETE FROM video_sources")
    suspend fun clearAll()
}

@Dao
interface LiveSourceDao {
    @Query("SELECT * FROM live_sources")
    fun all(): Flow<List<LiveSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: LiveSourceEntity)

    @Query("DELETE FROM live_sources")
    suspend fun clearAll()
}

@Dao
interface MusicSourceDao {
    @Query("SELECT * FROM music_sources ORDER BY name")
    fun all(): Flow<List<MusicSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: MusicSourceEntity)

    @Query("DELETE FROM music_sources")
    suspend fun clearAll()
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<EpgChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_channels")
    suspend fun clearChannels()

    @Query("DELETE FROM epg_programs")
    suspend fun clearPrograms()

    @Query("SELECT * FROM epg_channels")
    fun channels(): Flow<List<EpgChannelEntity>>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId ORDER BY startTime")
    fun programs(channelId: String): Flow<List<EpgProgramEntity>>
}

@Dao
interface PlayHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: PlayHistoryEntity)

    @Query("SELECT * FROM play_history WHERE module = :module ORDER BY updated DESC")
    fun byModule(module: String): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE sourceId = :sourceId AND contentId = :contentId LIMIT 1")
    suspend fun findByContent(sourceId: String, contentId: String): PlayHistoryEntity?
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM source_subscriptions")
    fun all(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM source_subscriptions WHERE subId = :id")
    suspend fun byId(id: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sub: SubscriptionEntity)

    @Query("DELETE FROM source_subscriptions WHERE subId = :id")
    suspend fun remove(id: String)
}

@Dao
interface SourceLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(log: SourceLogEntity)

    @Query("SELECT * FROM source_logs ORDER BY ts DESC LIMIT 500")
    fun recent(): Flow<List<SourceLogEntity>>

    @Query("DELETE FROM source_logs")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteEntity)

    @Query("SELECT * FROM favorites WHERE module = :module ORDER BY favoriteTime DESC")
    fun byModule(module: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE module = :module AND contentId = :contentId")
    suspend fun find(module: String, contentId: String): FavoriteEntity?

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM favorites WHERE module = :module")
    suspend fun clearModule(module: String)
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(q: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY ts DESC LIMIT 50")
    fun recent(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
