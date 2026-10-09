package com.aggregator.shell.core.ai

import android.content.Context
import com.aggregator.shell.core.common.LlmConfigKeys
import com.aggregator.shell.core.common.ModuleType
import com.aggregator.shell.core.data.di.appDataStore
import com.aggregator.shell.core.data.local.entity.SourceLogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Cloud LLM assistant backed by an OpenAI-compatible endpoint.
 *
 * The base URL and API key are read at call time from the local DataStore
 * (`LlmConfigKeys.BASE_URL` / `LlmConfigKeys.API_KEY`).  When either value
 * is blank the caller should fall back to [HeuristicAssistant].
 */
class CloudLlmAssistant @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext
    private val context: Context
) : AiSourceAssistant {

    private suspend fun chat(messages: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        val cfg = llmConfigSync()
            ?: throw IllegalStateException("LLM 未配置，请在设置中填入 Base URL 与 API Key")
        val (baseUrl, apiKey) = cfg

        val body = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", JSONArray(messages.map { (role, content) ->
                JSONObject().put("role", role).put("content", content)
            }))
            put("temperature", 0.3)
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw Exception("LLM 请求失败 HTTP ${r.code}: ${r.body?.string().orEmpty().take(200)}")
            r.body?.string() ?: throw Exception("LLM 响应为空")
        }

        runCatching {
            JSONObject(resp)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }.getOrDefault(resp)
    }

    private fun llmConfigSync(): Pair<String, String>? {
        val prefs = runBlocking { context.appDataStore.data.first() }
        val baseUrl = prefs[LlmConfigKeys.BASE_URL]?.trim()
        val apiKey = prefs[LlmConfigKeys.API_KEY]?.trim()
        return if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) null
        else baseUrl.removeSuffix("/chat/completions") to apiKey
    }

    override suspend fun generateSource(targetUrl: String, module: ModuleType, sampleHtml: String?): SourceGenerationResult {
        val prompt = buildString {
            append("You are a source-rule generator for a media aggregator app. ")
            append("Module: $module. Target URL: $targetUrl. ")
            if (!sampleHtml.isNullOrBlank()) {
                append("Sample HTML (first 3000 chars): ${sampleHtml.take(3000)}\n")
            }
            append("Return a JSON object with keys: name, module, url, ruleList, ruleTitle, ruleAuthor, ruleCover. ")
            append("Use JSONPath or CSS selectors. Do not include markdown or explanations outside the JSON.")
        }
        val raw = chat(listOf("system" to "You output only valid JSON.", "user" to prompt))
        val json = runCatching { JSONObject(raw) }.getOrNull()
        return SourceGenerationResult(
            config = json?.toString() ?: raw,
            module = module,
            explain = "云端 LLM 已生成候选源配置。",
            validated = json != null
        )
    }

    override suspend fun repairSource(original: String, logs: List<SourceLogEntity>, module: ModuleType): SourceRepairResult {
        val logSummary = logs.take(20).joinToString("\n") { l ->
            "- ${l.url} [${l.status}] ${l.category} ${l.detail}"
        }
        val prompt = buildString {
            append("Fix a broken source config for module $module. Original config:\n$original\n\n")
            append("Recent error logs:\n$logSummary\n\n")
            append("Return a JSON object with the corrected config. Explain changes in the 'changes' array. ")
            append("Output only JSON.")
        }
        val raw = chat(listOf("system" to "You output only valid JSON.", "user" to prompt))
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val changes = json?.optJSONArray("changes")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: listOf("LLM 返回内容解析失败，请检查输出。")
        return SourceRepairResult(
            config = json?.optString("config", original) ?: original,
            changes = changes,
            validated = json != null
        )
    }

    override suspend fun analyzeFailure(logs: List<SourceLogEntity>): FailureAnalysis {
        val logSummary = logs.take(20).joinToString("\n") { l ->
            "- ${l.url} [${l.status}] ${l.category} ${l.detail}"
        }
        val prompt = buildString {
            append("Analyze these source failure logs and identify the root cause. Logs:\n$logSummary\n\n")
            append("Return JSON: {\"rootCause\":\"...\", \"suggestedFix\":\"...\"}. Output only JSON.")
        }
        val raw = chat(listOf("system" to "You output only valid JSON.", "user" to prompt))
        val json = runCatching { JSONObject(raw) }.getOrNull()
        return FailureAnalysis(
            rootCause = json?.optString("rootCause") ?: "无法解析 LLM 响应：$raw",
            suggestedFix = json?.optString("suggestedFix") ?: "请人工检查日志。"
        )
    }
}
