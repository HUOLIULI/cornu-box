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
        FavoriteEntity::class,
        SearchHistoryEntity::class
    ],
    version = 2,
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
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        /**
         * Build a non-Hilt database instance for unit tests.
         * The caller is responsible for closing the returned instance.
         */
        fun createForTest(context: android.content.Context, name: String): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(Migrations.MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
