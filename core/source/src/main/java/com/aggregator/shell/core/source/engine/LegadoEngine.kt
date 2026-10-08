package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.Chapter
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.parse.RuleParser
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * Legado-compatible book source engine.
 *
 * 一个 Legado 书源是一个 JSON 块，含 `ruleSearch` / `ruleToc` / `ruleContent`，
 * 规则值由 [RuleParser] 解析。引擎按 Legado 语义：
 * 1. 由 `ruleSearch.bookList`（缺省 `$.data.books`）解析列表容器；
 * 2. 对每个元素用 `ruleSearch.id/name/author/coverUrl/bookUrl` 逐条求值；
 * 3. `ruleToc.chapterList` 解析章节列表，`chapterName/chapterUrl` 逐条求值。
 *
 * 内置演示源（example.com 域名）请求失败或不可达时返回本地演示数据，
 * 保证壳子开箱即用、UI 有内容可看。
 */
class LegadoEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor
) : ReaderEngine {

    private val parser = RuleParser(jsExecutor)

    override suspend fun search(keyword: String, page: Int): List<BookResult> {
        val sourceJson = fetchBookSourceJson() ?: return emptyList()
        val source = JSONObject(sourceJson)
        val rule = source.optJSONObject("ruleSearch") ?: return emptyList()
        val searchUrl = source.optString("searchUrl")
        val vars = mapOf("key" to keyword, "page" to page.toString())

        val url = searchUrl
            .replace("{{key}}", URLEncoder.encode(keyword, "UTF-8"))
            .replace("{{page}}", page.toString())
        val body = get(url)
        if (body.isBlank()) return emptyList()

        val listRule = rule.optString("bookList").ifBlank { "$.data.books" }
        val elements = parser.elements(body, listRule)
        val sourceName = source.optString("bookSourceName", "内置演示书源")
        return elements.mapNotNull { el ->
            runCatching {
                val id = parser.singleOn(el, rule.optString("id"), vars)
                val name = parser.singleOn(el, rule.optString("name"), vars)
                if (name.isBlank()) return@mapNotNull null
                BookResult(
                    id = id.ifEmpty { "bk-${UUID.randomUUID()}" },
                    name = name,
                    author = parser.singleOn(el, rule.optString("author"), vars),
                    coverUrl = parser.singleOn(el, rule.optString("coverUrl"), vars),
                    bookUrl = parser.singleOn(el, rule.optString("bookUrl"), vars).ifEmpty { id },
                    sourceName = sourceName
                )
            }.getOrNull()
        }
    }

    override suspend fun getToc(bookId: String): List<Chapter> {
        val sourceJson = fetchBookSourceJson() ?: return emptyList()
        val source = JSONObject(sourceJson)
        val rule = source.optJSONObject("ruleToc") ?: return emptyList()
        val vars = mapOf("bookId" to bookId)

        val body = if (isDemoUrl(bookId)) SourceBootstrap.demoBookTocBody() else get(bookId)
        if (body.isBlank()) return emptyList()

        val listRule = rule.optString("chapterList").ifBlank { "//div#list dd a" }
        val elements = parser.elements(body, listRule)
        return elements.mapIndexedNotNull { i, el ->
            runCatching {
                val title = parser.singleOn(el, rule.optString("chapterName"), vars)
                val url = parser.singleOn(el, rule.optString("chapterUrl"), vars)
                if (title.isBlank() && url.isBlank()) null
                else Chapter(index = i + 1, title = title, url = url)
            }.getOrNull()
        }
    }

    override suspend fun getContent(chapterId: String): String {
        val sourceJson = fetchBookSourceJson() ?: return ""
        val source = JSONObject(sourceJson)
        val rule = source.optJSONObject("ruleContent")
        val body = if (isDemoUrl(chapterId)) SourceBootstrap.demoBookContentBody(chapterId) else get(chapterId)
        if (body.isBlank()) return ""
        val content = rule?.optString("content")?.let { parser.single(body, it) } ?: body
        return content.ifBlank { body }
    }

    private suspend fun get(url: String): String {
        if (url.isBlank()) return ""
        // 内置演示源：仅搜索接口兜底；目录/正文由 getToc/getContent 直接短路
        if (isDemoUrl(url)) {
            return if (url.contains("/search/")) SourceBootstrap.demoBookSearchBody() else ""
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute().use { it.body?.string() ?: "" }
            }.getOrDefault("")
        }
    }

    private fun isDemoUrl(url: String): Boolean =
        url.contains("example.com") || url.startsWith("/")

    private suspend fun fetchBookSourceJson(): String? =
        // In full version: look up from Room book_sources. For shell demo: assets.
        try {
            SourceBootstrap.defaultLegadoSourceJson()
        } catch (e: Exception) { null }
}
