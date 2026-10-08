package com.aggregator.shell.core.media.di

import android.content.Context
import com.aggregator.shell.core.data.local.EpgDao
import com.aggregator.shell.core.media.danmaku.DanmakuSource
import com.aggregator.shell.core.media.danmaku.LocalDanmakuSource
import com.aggregator.shell.core.media.danmaku.RemoteDanmakuSource
import com.aggregator.shell.core.media.epg.EpgParser
import com.aggregator.shell.core.media.epg.EpgProvider
import com.aggregator.shell.core.media.player.ExoPlayerCore
import com.aggregator.shell.core.media.player.PlayerCore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    @Singleton
    abstract fun bindPlayerCore(impl: ExoPlayerCore): PlayerCore

    @Binds
    @Singleton
    abstract fun bindLocalDanmakuSource(impl: LocalDanmakuSource): LocalDanmakuSource

    companion object {
        /**
         * 弹幕源：提供 [RemoteDanmakuSource]（配置化）。未配置弹幕 API 端点时
         * 其内部自动回退 [LocalDanmakuSource] 演示弹幕，保证开箱可用。
         */
        @Provides
        @Singleton
        fun provideDanmakuSource(
            client: OkHttpClient,
            @ApplicationContext context: Context
        ): DanmakuSource = RemoteDanmakuSource(client, context)

        @Provides
        @Singleton
        fun provideEpgParser(client: OkHttpClient): EpgParser = EpgParser(client)

        @Provides
        @Singleton
        fun provideEpgProvider(
            client: OkHttpClient,
            parser: EpgParser,
            epgDao: EpgDao
        ): EpgProvider = EpgProvider(client, parser, epgDao)
    }
}
