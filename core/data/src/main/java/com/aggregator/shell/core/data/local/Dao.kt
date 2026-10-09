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

    @Query("SELECT * FROM bookshelf WHERE bookId = :id")
    suspend fun findById(id: String): BookshelfEntity?
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

    @Query("SELECT * FROM play_history WHERE id = :id")
    suspend fun byId(id: String): PlayHistoryEntity?

    @Query("SELECT * FROM play_history")
    suspend fun all(): List<PlayHistoryEntity>

    @Query("DELETE FROM play_history")
    suspend fun clearAll()
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
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedTime DESC")
    fun all(): Flow<List<FavoritesEntity>>

    @Query("SELECT * FROM favorites WHERE module = :module ORDER BY addedTime DESC")
    fun byModule(module: String): Flow<List<FavoritesEntity>>

    @Query("SELECT * FROM favorites WHERE category = :category ORDER BY addedTime DESC")
    fun byCategory(category: String): Flow<List<FavoritesEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoritesEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM favorites WHERE module = :module")
    suspend fun clearByModule(module: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("SELECT * FROM search_history WHERE module = :module ORDER BY lastUsed DESC LIMIT 20")
    fun byModule(module: String): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE module = :module AND query = :query")
    suspend fun remove(module: String, query: String)

    @Query("DELETE FROM search_history WHERE module = :module")
    suspend fun clearByModule(module: String)
}
