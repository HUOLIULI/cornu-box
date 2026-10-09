package com.aggregator.shell.core.data.di

import com.aggregator.shell.core.data.BackupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    fun provideBackupManager(impl: BackupManager): BackupManager = impl
}
