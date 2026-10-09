package com.aggregator.shell.core.ai.di

import android.content.Context
import com.aggregator.shell.core.ai.AiSourceAssistant
import com.aggregator.shell.core.ai.CloudLlmAssistant
import com.aggregator.shell.core.ai.HeuristicAssistant
import com.aggregator.shell.core.common.LlmConfigKeys
import com.aggregator.shell.core.data.di.appDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * Hilt module that binds [AiSourceAssistant] to a [LlmDelegatingAssistant].
 *
 * The delegating assistant reads the LLM config at call time and routes to
 * [CloudLlmAssistant] when a base URL + API key are present, otherwise to
 * [HeuristicAssistant].
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAssistant(
        @ApplicationContext context: Context,
        cloud: CloudLlmAssistant,
        heuristic: HeuristicAssistant
    ): AiSourceAssistant = LlmDelegatingAssistant(context, cloud, heuristic)
}

/**
 * Reads LLM config at call time; transparently falls back to the offline
 * heuristic when the user has not configured a cloud endpoint.
 */
class LlmDelegatingAssistant(
    @ApplicationContext private val context: Context,
    private val cloud: CloudLlmAssistant,
    private val heuristic: HeuristicAssistant
) : AiSourceAssistant {

    private fun useCloud(): Boolean = runCatching {
        val prefs = runBlocking { context.appDataStore.data.first() }
        (prefs[LlmConfigKeys.BASE_URL]?.trim()?.isNotBlank() == true)
            && (prefs[LlmConfigKeys.API_KEY]?.trim()?.isNotBlank() == true)
    }.getOrDefault(false)

    private val active: AiSourceAssistant
        get() = if (useCloud()) cloud else heuristic

    override suspend fun generateSource(targetUrl: String, module: com.aggregator.shell.core.common.ModuleType, sampleHtml: String?) =
        active.generateSource(targetUrl, module, sampleHtml)

    override suspend fun repairSource(original: String, logs: List<com.aggregator.shell.core.data.local.entity.SourceLogEntity>, module: com.aggregator.shell.core.common.ModuleType) =
        active.repairSource(original, logs, module)

    override suspend fun analyzeFailure(logs: List<com.aggregator.shell.core.data.local.entity.SourceLogEntity>) =
        active.analyzeFailure(logs)
}
