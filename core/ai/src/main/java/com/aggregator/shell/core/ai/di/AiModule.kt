package com.aggregator.shell.core.ai.di

import com.aggregator.shell.core.ai.AiSourceAssistant
import com.aggregator.shell.core.ai.HeuristicAssistant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideAssistant(): AiSourceAssistant = HeuristicAssistant()
}
