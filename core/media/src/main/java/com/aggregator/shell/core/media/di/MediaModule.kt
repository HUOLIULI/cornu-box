package com.aggregator.shell.core.media.di

import com.aggregator.shell.core.media.danmaku.DanmakuSource
import com.aggregator.shell.core.media.danmaku.LocalDanmakuSource
import com.aggregator.shell.core.media.epg.EpgParser
import com.aggregator.shell.core.media.epg.EpgProvider
import com.aggregator.shell.core.media.player.ExoPlayerCore
import com.aggregator.shell.core.media.player.PlayerCore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    abstract fun bindDanmakuSource(impl: LocalDanmakuSource): DanmakuSource

    companion object {
        @Provides
        @Singleton
        fun provideEpgParser(client: OkHttpClient): EpgParser = EpgParser(client)

        @Provides
        @Singleton
        fun provideEpgProvider(client: OkHttpClient, parser: EpgParser): EpgProvider =
            EpgProvider(client, parser)
    }
}
