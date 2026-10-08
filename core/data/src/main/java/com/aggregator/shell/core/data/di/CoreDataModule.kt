package com.aggregator.shell.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.aggregator.shell.core.data.local.AppDatabase
import com.aggregator.shell.core.data.local.BookSourceDao
import com.aggregator.shell.core.data.local.BookshelfDao
import com.aggregator.shell.core.data.local.EpgDao
import com.aggregator.shell.core.data.local.FavoriteDao
import com.aggregator.shell.core.data.local.LiveSourceDao
import com.aggregator.shell.core.data.local.MusicSourceDao
import com.aggregator.shell.core.data.local.Migrations
import com.aggregator.shell.core.data.local.PlayHistoryDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.SourceLogDao
import com.aggregator.shell.core.data.local.SubscriptionDao
import com.aggregator.shell.core.data.local.VideoSourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "shell_prefs")

@Module
@InstallIn(SingletonComponent::class)
object CoreDataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "shell.db")
            .addMigrations(Migrations.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideSubscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    @Singleton
    fun provideVideoSourceDao(db: AppDatabase): VideoSourceDao = db.videoSourceDao()

    @Provides
    @Singleton
    fun provideLiveSourceDao(db: AppDatabase): LiveSourceDao = db.liveSourceDao()

    @Provides
    @Singleton
    fun provideBookSourceDao(db: AppDatabase): BookSourceDao = db.bookSourceDao()

    @Provides
    @Singleton
    fun provideMusicSourceDao(db: AppDatabase): MusicSourceDao = db.musicSourceDao()

    @Provides
    @Singleton
    fun provideBookshelfDao(db: AppDatabase): BookshelfDao = db.bookshelfDao()

    @Provides
    @Singleton
    fun provideEpgDao(db: AppDatabase): EpgDao = db.epgDao()

    @Provides
    @Singleton
    fun providePlayHistoryDao(db: AppDatabase): PlayHistoryDao = db.playHistoryDao()

    @Provides
    @Singleton
    fun provideSourceLogDao(db: AppDatabase): SourceLogDao = db.sourceLogDao()

    @Provides
    @Singleton
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    @Singleton
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(client)
            .build()
}
