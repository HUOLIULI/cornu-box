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
            else -> {
                // Legado list items are often JSON objects; a bare field name like
                // "id" or "title" should resolve against the JSON element before
                // falling back to a CSS/HTML interpretation.
                if (r.contains("!") || r.contains("|") || r.contains("@")) {
                    css(input, r)
                } else {
                    jsonField(input, r).ifEmpty { css(input, r) }
                }
            }
        }
    }

    /**
     * If [input] is a JSON object (or array of objects), return the value of
     * field [field] as a string. Returns "" when the input is not JSON or the
     * field is absent, so callers can fall back to CSS extraction.
     */
    private fun jsonField(input: String, field: String): String {
        val trimmed = input.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return ""
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) "" else arr.getJSONObject(0).opt(field).toString()
                }
                else -> JSONObject(trimmed).opt(field).toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Extract every element of the array the rule points at.
     *
     * A Legado list rule is written against a *single* element, e.g. `$.list[0].name`.
     * The same rule expression is the template; [list] evaluates it for each element
     * of the underlying array and returns the raw element values so callers can apply
     * per-field rules on each element.
     *
     * @return the list element `String`s, or an empty list when nothing matches
     */
    suspend fun list(input: String, rule: String): List<String> {
        val r = rule.trim()
        if (r.isEmpty()) return emptyList()
        if (r.startsWith("$.") || r.startsWith("@Json:")) {
            val path = r.removePrefix("@Json:")
            return jsonPathList(input, path)
        }
        // CSS / regex / JS list: fall back to single-extraction joined form.
        val single = single(input, r)
        return if (single.isBlank()) emptyList() else listOf(single)
    }

    /**
     * Find the array in [input] that [path] points at and return its raw element strings.
     *
     * `$.data.list` -> each element of `data.list`
     * `$.list[0]`   -> each element of `list` (the index is a template marker)
     */
    private fun jsonPathList(input: String, path: String): List<String> {
        val tokens = path.removePrefix("$").split(".").filter { it.isNotBlank() }
        var current: Any? = input
        for (t in tokens) {
            when {
                t.startsWith("[") || t.endsWith("]") -> {
                    val key = t.substring(0, t.indexOf('['))
                    val arr = when (current) {
                        is String -> JSONObject(current).optJSONArray(key) ?: JSONArray()
                        is JSONObject -> current.optJSONArray(key) ?: JSONArray()
                        else -> JSONArray()
                    }
                    // return the raw elements of the array, not just index 0
                    return (0 until arr.length()).map {
                        when (val v = arr.get(it)) {
                            JSONObject.NULL -> ""
                            else -> v.toString()
                        }
                    }
                }
                else -> current = when (current) {
                    is String -> JSONObject(current).opt(t)
                    is JSONObject -> current.opt(t)
                    is JSONArray -> if (current.length() > 0) current.getJSONObject(0).opt(t) else null
                    else -> null
                }
            }
            if (current == null) return emptyList()
        }
        // no array marker in path: treat the resolved value as a single-element list
        return when (current) {
            JSONObject.NULL -> emptyList()
            null -> emptyList()
            else -> listOf(current.toString())
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
