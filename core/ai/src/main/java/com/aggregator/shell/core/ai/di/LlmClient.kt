package com.aggregator.shell.core.ai.di

import javax.inject.Qualifier

/**
 * 限定 LLM 调用专用的 [okhttp3.OkHttpClient]。
 *
 * 共享的 `OkHttpClient`（`CoreDataModule.provideOkHttp`）读超时为 15 秒，适合普通
 * 源 / 弹幕请求，但 LLM 生成往往需要数十秒，需要独立的更长超时。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LlmClient
