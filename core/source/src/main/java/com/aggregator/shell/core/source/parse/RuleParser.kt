package com.aggregator.shell.core.source.parse

import com.aggregator.shell.core.common.AppException
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Legado-equivalent rule parser (JSONPath / CSS / Regex / JS).
 * Subset sufficient for the shell's built-in demo and typical user sources.
 */
class RuleParser(private val jsExecutor: JsSandboxExecutor) {

    suspend fun single(input: String, rule: String): String {
        val r = rule.trim()
        if (r.isEmpty()) return ""
        return when {
            r.startsWith("$.") || r.startsWith("@Json:") ->
                jsonPath(input, r.removePrefix("@Json:"))
            r.startsWith("//") -> css(input, r.removePrefix("//"))
            r.startsWith(":") -> regex(input, r.removePrefix(":"))
            r.startsWith("@JS:") -> js(input, r.removePrefix("@JS:"))
            r.startsWith("@CSS:") -> css(input, r.removePrefix("@CSS:"))
            else -> css(input, r)
        }
    }

    /**
     * JSONPath: supports `$.a.b.c` and array indexing `$.a[0]` / `$.a[1].b`.
     * Falls back to "" on missing path.
     */
    private fun jsonPath(input: String, path: String): String {
        val p = path.removePrefix("$")
        val tokens = p.split(".").filter { it.isNotBlank() }
        var current: Any? = input
        for (t in tokens) {
            when {
                t.startsWith("[") || t.endsWith("]") -> {
                    // e.g. "books[0]" -> key "books", index 0
                    val key = t.substring(0, t.indexOf('['))
                    val idx = t.substring(t.indexOf('[') + 1, t.length - 1).toInt()
                    val arr = when (current) {
                        is String -> JSONObject(current).optJSONArray(key) ?: JSONArray()
                        is JSONObject -> current.optJSONArray(key) ?: JSONArray()
                        else -> JSONArray()
                    }
                    current = if (idx < arr.length()) arr.get(idx) else null
                }
                else -> current = when (current) {
                    is String -> JSONObject(current).opt(t)
                    is JSONObject -> current.opt(t)
                    is JSONArray -> if (current.length() > 0) current.getJSONObject(0).opt(t) else null
                    else -> null
                }
            }
            if (current == null) return ""
        }
        return when (current) {
            JSONObject.NULL -> ""
            is JSONObject -> current.toString()
            is JSONArray -> current.toString()
            else -> current.toString()
        }
    }

    private fun css(input: String, selector: String): String =
        try {
            val doc = Jsoup.parse(input)
            doc.select(selector).map { it.text() }.joinToString("\n")
        } catch (e: Exception) { "" }

    private fun regex(input: String, pattern: String): String =
        try {
            Regex(pattern).find(input)?.value ?: ""
        } catch (e: Exception) { "" }

    private suspend fun js(input: String, script: String): String {
        val result = jsExecutor.execute(
            script,
            bindings = mapOf("result" to input, "resultList" to emptyList<String>()),
            timeoutMillis = 10_000
        )
        return when (result) {
            is JsResult.Success -> result.value
            is JsResult.Failure -> throw AppException.RuleParseException(script, result.error.message ?: "JS 执行失败")
            is JsResult.Timeout -> throw AppException.JsExecutionTimeoutException(script)
        }
    }
}
