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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

    /** 读取 DataStore 判断是否走云端 LLM；在 IO 调度器上执行，避免阻塞调用方线程。 */
    private suspend fun useCloud(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.appDataStore.data.first()
            (prefs[LlmConfigKeys.BASE_URL]?.trim()?.isNotBlank() == true)
                && (prefs[LlmConfigKeys.API_KEY]?.trim()?.isNotBlank() == true)
        }.getOrDefault(false)
    }

    private suspend fun active(): AiSourceAssistant = if (useCloud()) cloud else heuristic

    override suspend fun generateSource(targetUrl: String, module: com.aggregator.shell.core.common.ModuleType, sampleHtml: String?) =
        active().generateSource(targetUrl, module, sampleHtml)

    override suspend fun repairSource(original: String, logs: List<com.aggregator.shell.core.data.local.entity.SourceLogEntity>, module: com.aggregator.shell.core.common.ModuleType) =
        active().repairSource(original, logs, module)

    override suspend fun analyzeFailure(logs: List<com.aggregator.shell.core.data.local.entity.SourceLogEntity>) =
        active().analyzeFailure(logs)
}
