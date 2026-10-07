package com.aggregator.shell.core.media.di

import com.aggregator.shell.core.media.player.ExoPlayerCore
import com.aggregator.shell.core.media.player.PlayerCore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    @Singleton
    abstract fun bindPlayerCore(impl: ExoPlayerCore): PlayerCore
}
