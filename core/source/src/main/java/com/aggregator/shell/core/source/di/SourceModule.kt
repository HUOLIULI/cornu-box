package com.aggregator.shell.core.source.di

import com.aggregator.shell.core.source.engine.LegadoEngine
import com.aggregator.shell.core.source.engine.LxMusicEngine
import com.aggregator.shell.core.source.engine.TvBoxEngine
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.search.AggregateVideoSearch
import com.aggregator.shell.core.source.search.VideoSearchRepository
import com.aggregator.shell.core.source.search.VideoSearchRepositoryImpl
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

    /**
     * 已启用视频引擎列表：聚合搜索层并发查询这里的每个引擎。
     * 新增视频源引擎（如 Cinetry/AVBox 风格的 TVBox 变体）时在此追加，
     * 或通过 Room 源表的 enabled 开关按条件过滤。
     */
    @Provides
    @Singleton
    fun provideVideoEngines(
        videoEngine: VideoEngine
    ): List<VideoEngine> =
        listOf(videoEngine)

    @Provides
    @Singleton
    fun provideAggregateVideoSearch(engines: List<VideoEngine>): AggregateVideoSearch =
        AggregateVideoSearch(engines)

    @Provides
    @Singleton
    fun provideVideoSearchRepository(
        aggregate: AggregateVideoSearch
    ): VideoSearchRepository =
        VideoSearchRepositoryImpl(aggregate)

    @Provides
    @Singleton
    fun provideMusicEngine(
        client: OkHttpClient,
        js: JsSandboxExecutor
    ): MusicEngine =
        LxMusicEngine(client, js)
}
