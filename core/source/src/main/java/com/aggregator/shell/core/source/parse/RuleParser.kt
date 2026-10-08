package com.aggregator.shell.core.source.parse

import com.aggregator.shell.core.common.AppException
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject

/**
 * Legado 兼容规则解析器（JSONPath / CSS / Regex / JS 子集）。
 *
 * 规则语义对齐 Legado：
 * - [single]：取第一个命中值；CSS 命中多个元素只取首个文本/属性
 * - [list]：取全部命中值（JSONPath `[*]`、CSS 集合）
 * - [elements]：解析列表容器，返回元素列表供引擎逐条解析
 * - [singleOn]：在单个元素（JSONObject / Element）上求值
 * - `@` 拼接：`ruleA@ruleB`，ruleB 中的 `{{result}}` 会被 ruleA 的结果替换
 * - `{{key}} / {{page}} / {{bookId}}` 等变量经 [vars] 替换
 * - 裸 `text` 取元素文本；`href/src/alt/data-src` 等在元素上下文中按属性处理
 */
class RuleParser(private val jsExecutor: JsSandboxExecutor) {

    private val attrNames = setOf("href", "src", "alt", "data-src", "title", "content")

    /** 整页取单个值。 */
    suspend fun single(input: String, rule: String, vars: Map<String, String> = emptyMap()): String {
        val r = rule.trim()
        if (r.isEmpty()) return ""
        if (isDirective(r)) return singleCore(input, r, vars)
        if (r.contains("@")) {
            val idx = r.indexOf('@')
            val left = r.substring(0, idx)
            val right = r.substring(idx + 1)
            val base = single(input, left, vars)
            if (base.isEmpty()) return ""
            return joinRight(input, base, right, vars)
        }
        return singleCore(input, r, vars)
    }

    /** 整页取列表（用于标题集合等）。 */
    suspend fun list(input: String, rule: String, vars: Map<String, String> = emptyMap()): List<String> {
        val r = rule.trim()
        if (r.isEmpty()) return emptyList()
        if (isDirective(r)) return listCore(input, r, vars)
        if (r.contains("@")) {
            val idx = r.indexOf('@')
            val left = r.substring(0, idx)
            val right = r.substring(idx + 1)
            val bases = list(input, left, vars)
            return bases.map { base -> joinRight(input, base, right, vars) }
        }
        return listCore(input, r, vars)
    }

    /** 解析列表容器，返回元素列表（JSONObject / Element），供引擎逐条解析。 */
    fun elements(input: String, listRule: String): List<Any> {
        val r = listRule.trim()
        return when {
            r.isEmpty() -> emptyList()
            r.startsWith("$.") || r.startsWith("@Json:") -> jsonPathElements(input, r.removePrefix("@Json:"))
            r.startsWith("//") -> cssElements(input, r.removePrefix("//"))
            r.startsWith("@CSS:") -> cssElements(input, r.removePrefix("@CSS:"))
            r.startsWith("@JS:") -> emptyList() // JS 列表由引擎另行处理
            else -> cssElements(input, r)
        }
    }

    /** 在单个元素上求 single 值。 */
    suspend fun singleOn(element: Any, rule: String, vars: Map<String, String> = emptyMap()): String {
        val r = rule.trim()
        if (r.isEmpty()) return ""
        if (isDirective(r)) return singleCoreOn(element, r, vars)
        if (r.contains("@")) {
            val idx = r.indexOf('@')
            val left = r.substring(0, idx)
            val right = r.substring(idx + 1)
            val base = singleOn(element, left, vars)
            if (base.isEmpty()) return ""
            return joinRight(elementToString(element), base, right, vars)
        }
        return singleCoreOn(element, r, vars)
    }

    // ---------- 内部：单值 ----------

    private suspend fun singleCore(input: String, rule: String, vars: Map<String, String>): String {
        val r = rule.trim()
        if (r.isEmpty()) return ""
        return when {
            r.startsWith("{{") && r.endsWith("}}") -> r.replaceVars(vars)
            r.startsWith("$.") -> jsonPathFirst(input, r)
            r.startsWith("@Json:") -> jsonPathFirst(input, r.removePrefix("@Json:"))
            r.startsWith("//") -> cssFirst(input, r.removePrefix("//"))
            r.startsWith("@CSS:") -> cssFirst(input, r.removePrefix("@CSS:"))
            r.startsWith(":") -> regex(input, r.removePrefix(":"))
            r.startsWith("@JS:") -> js(input, r.removePrefix("@JS:"))
            r == "text" -> Jsoup.parse(input).text()
            r == "html" -> Jsoup.parse(input).html()
            r in attrNames -> Jsoup.parse(input).select("body").firstOrNull()?.attr(r) ?: ""
            else -> cssFirst(input, r)
        }
    }

    private suspend fun singleCoreOn(element: Any, rule: String, vars: Map<String, String>): String {
        val r = rule.trim()
        if (r.isEmpty()) return ""
        return when (element) {
            is Element -> when {
                r.startsWith("$.") || r.startsWith("@Json:") ->
                    jsonPathFirst(elementToString(element), r.removePrefix("@Json:"))
                r.startsWith("//") -> element.select(r.removePrefix("//")).firstOrNull()?.let { extract(it, "text") } ?: ""
                r.startsWith("@CSS:") -> element.select(r.removePrefix("@CSS:")).firstOrNull()?.let { extract(it, "text") } ?: ""
                r.startsWith(":") -> regex(elementToString(element), r.removePrefix(":"))
                r.startsWith("@JS:") -> js(elementToString(element), r.removePrefix("@JS:"))
                r == "text" -> element.text()
                r == "html" -> element.html()
                r in attrNames -> element.attr(r)
                else -> {
                    val (sel, attr) = splitCssAttr(r)
                    element.select(sel).firstOrNull()?.let { extract(it, attr) } ?: ""
                }
            }
            is JSONObject -> when {
                r.startsWith("$.") -> jsonPathFirst(element.toString(), r)
                r.startsWith("@Json:") -> jsonPathFirst(element.toString(), r.removePrefix("@Json:"))
                r.startsWith(":") -> regex(element.toString(), r.removePrefix(":"))
                r.startsWith("@JS:") -> js(element.toString(), r.removePrefix("@JS:"))
                r.startsWith("//") || r.startsWith("@CSS:") -> "" // JSON 元素无 CSS
                r == "text" -> element.toString()
                else -> element.optString(r).let { if (it == "null" || it == "JSONObject.NULL") "" else it }
            }
            is JSONArray -> if (element.length() > 0) singleCoreOn(element.get(0), r, vars) else ""
            else -> singleCore(elementToString(element), r, vars)
        }
    }

    private suspend fun joinRight(input: String, base: String, right: String, vars: Map<String, String>): String {
        val rr = right.trim()
        if (rr.contains("{{result}}")) {
            return rr.replace("{{result}}", base).replaceVars(vars)
        }
        if (rr.contains("{{")) {
            return rr.replaceVars(vars)
        }
        // 右侧仍是规则（如 @href、//a@text），在 base 结果上求值
        return singleCore(base, rr, vars)
    }

    // ---------- 内部：列表 ----------

    private suspend fun listCore(input: String, rule: String, vars: Map<String, String>): List<String> {
        val r = rule.trim()
        return when {
            r.startsWith("$.") || r.startsWith("@Json:") ->
                jsonPathElements(input, r.removePrefix("@Json:")).map { stringify(it) }
            r.startsWith("//") -> cssElements(input, r.removePrefix("//")).map { elementText(it) }
            r.startsWith("@CSS:") -> cssElements(input, r.removePrefix("@CSS:")).map { elementText(it) }
            r.startsWith("@JS:") -> jsList(input, r.removePrefix("@JS:"))
            else -> cssElements(input, r).map { elementText(it) }
        }
    }

    // ---------- 内部：JSONPath ----------

    private fun jsonPathFirst(input: String, path: String): String {
        val result = walk(input, tokens(path))
        return when (result) {
            null -> ""
            is List<*> -> if (result.isEmpty()) "" else stringify(result.first())
            else -> stringify(result)
        }
    }

    private fun jsonPathElements(input: String, path: String): List<Any> {
        val result = walk(input, tokens(path))
        return when (result) {
            is JSONArray -> (0 until result.length()).map { result.get(it) }
            is List<*> -> result.mapNotNull { it }
            null -> emptyList()
            else -> listOf(result)
        }
    }

    private fun tokens(path: String): List<String> {
        val p = path.removePrefix("$").trim()
        if (p.isEmpty()) return emptyList()
        return p.split(".").filter { it.isNotBlank() }
    }

    private fun walk(current: Any?, tokens: List<String>): Any? {
        var c = current
        for (t in tokens) {
            c = when (c) {
                is List<*> -> c.mapNotNull { step(it, t) }
                else -> step(c, t)
            }
            if (c == null) return null
        }
        return c
    }

    private fun step(current: Any?, token: String): Any? {
        // token 形如 "books" / "books[0]" / "books[*]"
        val bracket = token.indexOf('[')
        if (bracket < 0) {
            return when (current) {
                is String -> JSONObject(current).opt(token)
                is JSONObject -> current.opt(token)
                is JSONArray -> if (current.length() > 0) current.getJSONObject(0).opt(token) else null
                else -> null
            }
        }
        val name = token.substring(0, bracket)
        val expr = token.substring(bracket + 1, token.length - 1)
        val arr = when (current) {
            is String -> JSONObject(current).optJSONArray(name)
            is JSONObject -> current.optJSONArray(name)
            is JSONArray -> if (current.length() > 0) (current.get(0) as? JSONObject)?.optJSONArray(name) else null
            else -> null
        } ?: return null
        return if (expr == "*") {
            (0 until arr.length()).map { arr.get(it) }
        } else {
            val idx = expr.toIntOrNull() ?: return null
            if (idx in 0 until arr.length()) arr.get(idx) else null
        }
    }

    private fun stringify(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is JSONObject -> v.toString()
        is JSONArray -> v.toString()
        else -> v.toString()
    }

    // ---------- 内部：CSS ----------

    private fun cssFirst(input: String, selector: String): String {
        val (sel, attr) = splitCssAttr(selector)
        val doc = Jsoup.parse(input)
        return doc.select(sel).firstOrNull()?.let { extract(it, attr) } ?: ""
    }

    private fun cssElements(input: String, selector: String): List<Any> {
        val (sel, _) = splitCssAttr(selector)
        val doc = Jsoup.parse(input)
        return doc.select(sel).map { it as Any }
    }

    private fun splitCssAttr(selector: String): Pair<String, String> {
        val idx = selector.lastIndexOf('@')
        if (idx > 0) {
            val attr = selector.substring(idx + 1).trim()
            if (attr.matches(Regex("""[a-zA-Z][\w-]*"""))) {
                return selector.substring(0, idx).trim() to attr
            }
        }
        return selector.trim() to "text"
    }

    private fun extract(el: Element, attr: String): String = when (attr) {
        "text" -> el.text()
        "html" -> el.html()
        else -> el.attr(attr)
    }

    private fun elementText(v: Any): String = when (v) {
        is Element -> v.text()
        else -> stringify(v)
    }

    private fun elementToString(element: Any): String = when (element) {
        is Element -> element.outerHtml()
        else -> stringify(element)
    }

    // ---------- 内部：Regex / JS ----------

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

    private suspend fun jsList(input: String, script: String): List<String> {
        val result = jsExecutor.execute(
            script,
            bindings = mapOf("result" to input, "resultList" to emptyList<String>()),
            timeoutMillis = 10_000
        )
        return when (result) {
            is JsResult.Success -> {
                val v = result.value.trim()
                if (v.startsWith("[")) {
                    runCatching { JSONArray(v) }.getOrNull()?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }
                    } ?: emptyList()
                } else if (v.isEmpty()) emptyList() else listOf(v)
            }
            is JsResult.Failure -> throw AppException.RuleParseException(script, result.error.message ?: "JS 执行失败")
            is JsResult.Timeout -> throw AppException.JsExecutionTimeoutException(script)
        }
    }

    private fun String.replaceVars(vars: Map<String, String>): String =
        Regex("""\{\{\s*(\w+)\s*}}""").replace(this) { m ->
            vars[m.groupValues[1]] ?: ""
        }

    private fun isDirective(rule: String) =
        rule.startsWith("@Json:") || rule.startsWith("@JS:") || rule.startsWith("@CSS:")
}
