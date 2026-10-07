package com.aggregator.shell.core.data.di

import com.aggregator.shell.core.data.*
import com.aggregator.shell.core.data.source.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceRepoModule {
    @Binds
    abstract fun bindVideoRepo(impl: VideoSourceRepoImpl): VideoSourceRepo

    @Binds
    abstract fun bindReaderRepo(impl: ReaderSourceRepoImpl): ReaderSourceRepo

    @Binds
    abstract fun bindMusicRepo(impl: MusicSourceRepoImpl): MusicSourceRepo

    @Binds
    abstract fun bindSubscription(impl: SubscriptionManagerImpl): SubscriptionManager
}
