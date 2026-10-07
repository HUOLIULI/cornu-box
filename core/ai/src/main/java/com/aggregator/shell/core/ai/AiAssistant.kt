package com.aggregator.shell.core.ai

import com.aggregator.shell.core.common.ModuleType
import com.aggregator.shell.core.data.local.entity.SourceLogEntity
import javax.inject.Inject

/**
 * Result of an AI source generation/repair pass. The `suggestion` is a
 * ready-to-import source config; `explain` describes what was inferred.
 */
data class SourceGenerationResult(
    val config: String,
    val module: ModuleType,
    val explain: String,
    val validated: Boolean = false
)

data class SourceRepairResult(
    val config: String,
    val changes: List<String>,
    val validated: Boolean = false
)

data class FailureAnalysis(
    val rootCause: String,
    val suggestedFix: String
)

/**
 * AI source assistant. The cloud implementation calls a user-supplied LLM
 * endpoint; the local heuristic implementation runs offline.
 *
 * The LLM endpoint and key are owned by the user (never read from the
 * build environment). See `settings` module for where they are entered.
 */
interface AiSourceAssistant {
    suspend fun generateSource(targetUrl: String, module: ModuleType, sampleHtml: String?): SourceGenerationResult
    suspend fun repairSource(original: String, logs: List<SourceLogEntity>, module: ModuleType): SourceRepairResult
    suspend fun analyzeFailure(logs: List<SourceLogEntity>): FailureAnalysis
}

/**
 * Offline rule-mining heuristic. No network; derives a candidate JSONPath /
 * CSS rule from a sample HTML payload. Used when no LLM key is configured.
 */
class HeuristicAssistant @Inject constructor() : AiSourceAssistant {
    override suspend fun generateSource(targetUrl: String, module: ModuleType, sampleHtml: String?): SourceGenerationResult {
        val listRule = if (!sampleHtml.isNullOrBlank() && sampleHtml.contains("<li")) "li" else ""
        val titleRule = if (sampleHtml?.contains("title") == true) ".title" else "a"
        return SourceGenerationResult(
            config = """{"name":"generated","module":"$module","url":"$targetUrl","ruleList":"$listRule","ruleTitle":"$titleRule"}""",
            module = module,
            explain = "离线启发式：从样例 HTML 中识别出列表容器 $listRule 与标题元素 $titleRule。建议补充作者、封面、正文规则。",
            validated = sampleHtml.isNullOrBlank()
        )
    }

    override suspend fun repairSource(
        original: String,
        logs: List<SourceLogEntity>,
        module: ModuleType
    ): SourceRepairResult {
        val failures = logs.count { it.status >= 400 || it.category == "rule" }
        return SourceRepairResult(
            config = original,
            changes = listOf(
                "检测到 $failures 条失败记录",
                "建议为失败规则增加 @js 兜底与 15s 超时",
                "网络 4xx/5xx 记录建议切换备用源"
            ),
            validated = false
        )
    }

    override suspend fun analyzeFailure(logs: List<SourceLogEntity>): FailureAnalysis {
        val httpFail = logs.count { it.status in 400..599 }
        val ruleFail = logs.count { it.category == "rule" }
        return FailureAnalysis(
            rootCause = if (httpFail > ruleFail) "网络层失败（HTTP 错误）" else "规则层解析失败",
            suggestedFix = if (httpFail > ruleFail) "检查源 URL / Referer / Cookie 与证书" else "修正 JSONPath / CSS 选择器，检查列表容器"
        )
    }
}
