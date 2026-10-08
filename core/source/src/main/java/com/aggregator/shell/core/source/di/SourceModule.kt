package com.aggregator.shell.core.source.di

import com.aggregator.shell.core.data.local.BookSourceDao
import com.aggregator.shell.core.data.local.LiveSourceDao
import com.aggregator.shell.core.data.local.MusicSourceDao
import com.aggregator.shell.core.data.local.VideoSourceDao
import com.aggregator.shell.core.source.engine.LegadoEngine
import com.aggregator.shell.core.source.engine.LxMusicEngine
import com.aggregator.shell.core.source.engine.TvBoxEngine
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.registry.FallbackSourceProvider
import com.aggregator.shell.core.source.registry.RoomSourceProvider
import com.aggregator.shell.core.source.registry.SourceProvider
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import com.aggregator.shell.core.source.sandbox.PythonRuntime
import com.aggregator.shell.core.source.sandbox.NoOpPythonRuntime
import com.aggregator.shell.core.source.sandbox.RhinoJsExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Registers the built-in source engines. Feature modules depend only on the
 * capability interfaces ([ReaderEngine], [VideoEngine], [MusicEngine]); the
 * concrete engines are provided here so they can be swapped without touching
 * feature code.
 *
 * The engines receive a [SourceProvider] that resolves user-imported sources
 * from Room source tables; when the tables are empty, [FallbackSourceProvider]
 * supplies the built-in demo sources so the shell still has content out of the box.
 */
@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideJsExecutor(): JsSandboxExecutor = RhinoJsExecutor()

    @Provides
    @Singleton
    fun providePythonRuntime(): PythonRuntime = NoOpPythonRuntime()

    @Provides
    @Singleton
    fun provideSourceProvider(
        videoSourceDao: VideoSourceDao,
        bookSourceDao: BookSourceDao,
        liveSourceDao: LiveSourceDao,
        musicSourceDao: MusicSourceDao
    ): SourceProvider = RoomSourceProvider(
        videoSourceDao = videoSourceDao,
        bookSourceDao = bookSourceDao,
        liveSourceDao = liveSourceDao,
        musicSourceDao = musicSourceDao,
        fallback = FallbackSourceProvider()
    )

    @Provides
    @Singleton
    fun provideReaderEngine(
        client: OkHttpClient,
        js: JsSandboxExecutor,
        sourceProvider: SourceProvider
    ): ReaderEngine =
        LegadoEngine(client, js, sourceProvider)

    @Provides
    @Singleton
    fun provideVideoEngine(
        client: OkHttpClient,
        js: JsSandboxExecutor,
        py: PythonRuntime,
        sourceProvider: SourceProvider
    ): VideoEngine =
        TvBoxEngine(client, js, py, sourceProvider)

    @Provides
    @Singleton
    fun provideMusicEngine(
        client: OkHttpClient,
        js: JsSandboxExecutor,
        sourceProvider: SourceProvider
    ): MusicEngine =
        LxMusicEngine(client, js, sourceProvider)
}
