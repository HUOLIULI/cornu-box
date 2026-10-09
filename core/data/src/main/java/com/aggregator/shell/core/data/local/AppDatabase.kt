package com.aggregator.shell.core.data.local

import androidx.room.*
import com.aggregator.shell.core.data.local.entity.*

@Database(
    entities = [
        BookSourceEntity::class,
        BookshelfEntity::class,
        VideoSourceEntity::class,
        LiveSourceEntity::class,
        MusicSourceEntity::class,
        EpgChannelEntity::class,
        EpgProgramEntity::class,
        PlayHistoryEntity::class,
        SubscriptionEntity::class,
        SourceLogEntity::class,
        FavoritesEntity::class,
        SearchHistoryEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookSourceDao(): BookSourceDao
    abstract fun bookshelfDao(): BookshelfDao
    abstract fun videoSourceDao(): VideoSourceDao
    abstract fun liveSourceDao(): LiveSourceDao
    abstract fun musicSourceDao(): MusicSourceDao
    abstract fun epgDao(): EpgDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun sourceLogDao(): SourceLogDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
