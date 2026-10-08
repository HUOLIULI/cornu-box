package com.aggregator.shell.core.source.di

import com.aggregator.shell.core.source.engine.LegadoEngine
import com.aggregator.shell.core.source.engine.LxMusicEngine
import com.aggregator.shell.core.source.engine.TvBoxEngine
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.api.VideoEngine
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
 * The engines receive the Room source-table DAOs so they resolve user-imported
 * sources; each falls back to the built-in demo when the table is empty.
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
    fun provideReaderEngine(
        client: OkHttpClient,
        js: JsSandboxExecutor
    ): ReaderEngine =
        LegadoEngine(client, js)

    @Provides
    @Singleton
    fun provideVideoEngine(
        client: OkHttpClient,
        js: JsSandboxExecutor,
        py: PythonRuntime
    ): VideoEngine =
        TvBoxEngine(client, js, py)

    @Provides
    @Singleton
    fun provideMusicEngine(
        client: OkHttpClient,
        js: JsSandboxExecutor
    ): MusicEngine =
        LxMusicEngine(client, js)
}
