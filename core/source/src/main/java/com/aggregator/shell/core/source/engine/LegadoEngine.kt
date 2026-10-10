package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.Chapter
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.parse.RuleParser
import com.aggregator.shell.core.source.registry.BookSourceDef
import com.aggregator.shell.core.source.registry.SourceProvider
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
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
 * 通过 [SourceProvider] 读取 Room 源表中的全部已启用书源并发聚合搜索；
 * 源表为空时由 [SourceProvider] 回退内置演示源，保证壳子开箱即用。
 */
class LegadoEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor,
    private val sourceProvider: SourceProvider
) : ReaderEngine {

    private val parser = RuleParser(jsExecutor)

    override suspend fun search(keyword: String, page: Int): List<BookResult> {
        val defs = sourceProvider.bookSources().filter { it.enabled }
        val ruleResults = defs.mapNotNull { def ->
            runCatching {
                val source = JSONObject(def.rawJson)
                // 单文件书源脚本（mainJs / format=js）：优先走 Rhino 沙盒执行
                if (source.optString("format", "").equals("js", true) || source.optString("mainJs").isNotBlank()) {
                    return@runCatching searchViaMainJs(def, keyword, page)
                }
                val rule = source.optJSONObject("ruleSearch") ?: return@runCatching null
                val searchUrl = source.optString("searchUrl")
                if (searchUrl.isBlank()) return@runCatching null
                val vars = mapOf("key" to keyword, "page" to page.toString())
                val url = searchUrl
                    .replace("{{key}}", URLEncoder.encode(keyword, "UTF-8"))
                    .replace("{{page}}", page.toString())
                val body = get(url)
                if (body.isBlank()) return@runCatching null
                val listRule = rule.optString("bookList").ifBlank { "$.data.books" }
                val elements = parser.elements(body, listRule)
                val sourceName = def.name
                elements.mapNotNull { el ->
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
            }.getOrDefault(emptyList())
        }
        return ruleResults.flatten()
    }

    /**
     * 单文件书源（Legado `mainJs` / `format=js`）：在 Rhino 沙盒内执行 `mainJs`，
     * 脚本可调用 `http(url)` 拉取内容，最终返回书籍 JSON 数组。受指令上限与超时保护，
     * 与 LxMusic 的 `lx` 桥同一套 [JsSandboxExecutor]，不引入额外 native。
     *
     * 约定返回 JSON：`[{"id","title","writer","cover","url"}]`。
     */
    private suspend fun searchViaMainJs(def: BookSourceDef, keyword: String, page: Int): List<BookResult> {
        val source = JSONObject(def.rawJson)
        val script = source.optString("mainJs").takeIf { it.isNotBlank() } ?: return emptyList()
        val result = jsExecutor.execute(
            script = script,
            bindings = mapOf(
                "key" to keyword,
                "page" to page,
                "http" to { url: String ->
                    kotlinx.coroutines.runBlocking { get(url) }
                },
                "baseUrl" to source.optString("bookSourceUrl", "")
            ),
            timeoutMillis = 15_000
        )
        val raw = when (result) {
            is JsResult.Success -> result.value.trim()
            is JsResult.Failure -> return emptyList()
            is JsResult.Timeout -> return emptyList()
        }
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val sourceName = def.name
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val id = o.optString("id").ifEmpty { "bk-${def.sourceId}-$i" }
                val name = o.optString("title").ifEmpty { o.optString("name") }
                if (name.isBlank()) return@mapNotNull null
                BookResult(
                    id = id,
                    name = name,
                    author = o.optString("writer", o.optString("author", "")),
                    coverUrl = o.optString("cover", o.optString("coverUrl", "")),
                    bookUrl = o.optString("url", o.optString("bookUrl", id)),
                    sourceName = sourceName
                )
            }.getOrNull()
        }
    }

    override suspend fun getToc(bookId: String): List<Chapter> {
        val def = sourceProvider.bookSources().firstOrNull { it.enabled } ?: return emptyList()
        val source = JSONObject(def.rawJson)
        val rule = source.optJSONObject("ruleToc") ?: return emptyList()
        val vars = mapOf("bookId" to bookId, "sourceId" to def.sourceId)

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
        val def = sourceProvider.bookSources().firstOrNull { it.enabled } ?: return ""
        val source = JSONObject(def.rawJson)
        val rule = source.optJSONObject("ruleContent")
        val body = if (isDemoUrl(chapterId)) SourceBootstrap.demoBookContentBody(chapterId) else get(chapterId)
        if (body.isBlank()) return ""
        val content = rule?.optString("content")?.let { parser.single(body, it) } ?: body
        return content.ifBlank { body }
    }

    private suspend fun get(url: String): String {
        if (url.isBlank()) return ""
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
}
