package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.common.AppLog
import com.aggregator.shell.core.common.NoOpLog
import com.aggregator.shell.core.data.local.BookSourceDao
import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.Chapter
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.parse.RuleParser
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Legado-compatible book source engine.
 *
 * A Legado source is a JSON blob with `ruleSearch` / `ruleToc` / `ruleContent`
 * blocks whose values are parsed by [RuleParser]. The engine fetches the
 * search / TOC URL described by the source, applies the JSONPath rules, and
 * returns structured results.
 *
 * Source payload resolution: an optional [bookSourceDao] supplies user-imported
 * sources from Room; when no enabled source is present, the built-in demo
 * bootstrap is used so the shell runs out of the box.
 */
class LegadoEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor,
    private val bookSourceDao: BookSourceDao? = null,
    private val log: AppLog = NoOpLog
) : ReaderEngine {

    private val parser = RuleParser(jsExecutor)

    override suspend fun search(keyword: String, page: Int): List<BookResult> {
        val sourceJson = resolveSourceJson() ?: run {
            log.w(TAG, "no book source available (Room empty + bootstrap failed)")
            return emptyList()
        }
        val source = try { JSONObject(sourceJson) } catch (e: Exception) {
            log.e(TAG, "book source JSON invalid", e)
            return emptyList()
        }
        val ruleSearch = source.optJSONObject("ruleSearch") ?: return emptyList()
        val searchUrl = source.optString("searchUrl")
        val url = searchUrl
            .replace("{{key}}", keyword)
            .replace("{{page}}", page.toString())

        val body = get(url)
        if (body.isBlank()) {
            log.w(TAG, "search returned empty body: $url")
            return emptyList()
        }

        // The list rule is written against a single element, e.g. `$.data.list[0]`.
        // `list(body, listRule)` returns the raw element values; each element is then
        // re-applied with per-field rules.
        val listRule = ruleSearch.optString("list", "$.data.list[0]")
        val elements = parser.list(body, listRule)
        if (elements.isEmpty()) {
            log.w(TAG, "no search results matched list rule: $listRule")
        }
        return elements.mapNotNull { element ->
            try {
                BookResult(
                    id = parser.single(element, ruleSearch.optString("id")).ifEmpty { "book-${elements.indexOf(element)}" },
                    name = parser.single(element, ruleSearch.optString("name")),
                    author = parser.single(element, ruleSearch.optString("author")),
                    coverUrl = parser.single(element, ruleSearch.optString("coverUrl")),
                    bookUrl = parser.single(element, ruleSearch.optString("bookUrl")),
                    sourceName = source.optString("bookSourceName")
                )
            } catch (e: Exception) {
                log.w(TAG, "failed to parse search element", e)
                null
            }
        }
    }

    override suspend fun getToc(bookId: String): List<Chapter> {
        val sourceJson = resolveSourceJson() ?: return emptyList()
        val source = try { JSONObject(sourceJson) } catch (e: Exception) {
            log.e(TAG, "book source JSON invalid", e)
            return emptyList()
        }
        val ruleToc = source.optJSONObject("ruleToc") ?: return emptyList()
        // For the shell, bookId is expected to be the full chapter-list URL.
        val body = get(bookId)
        if (body.isBlank()) {
            log.w(TAG, "TOC returned empty body: $bookId")
            return emptyList()
        }
        val listRule = ruleToc.optString("chapterList")
        val elements = if (listRule.isNotBlank()) parser.list(body, listRule) else emptyList()
        return elements.mapIndexedNotNull { i, element ->
            try {
                Chapter(
                    index = i,
                    title = parser.single(element, ruleToc.optString("chapterName")),
                    url = parser.single(element, ruleToc.optString("chapterUrl"))
                )
            } catch (e: Exception) {
                log.w(TAG, "failed to parse chapter $i", e)
                null
            }
        }
    }

    override suspend fun getContent(chapterId: String): String {
        val sourceJson = resolveSourceJson()
        val source = sourceJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val ruleContent = source?.optJSONObject("ruleContent")
        val body = get(chapterId)
        if (ruleContent == null || body.isBlank()) return body
        return parser.single(body, ruleContent.optString("content")).let {
            if (it.isBlank()) body else it
        }
    }

    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext ""
            client.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute().use { it.body?.string() ?: "" }
        }

    /**
     * Resolve the source JSON: prefer the first enabled source in Room; fall back
     * to the built-in demo bootstrap when Room is empty.
     */
    private suspend fun resolveSourceJson(): String? {
        bookSourceDao?.let { dao ->
            val list = runCatching { dao.all().first() }.getOrDefault(emptyList())
            val pick = list.firstOrNull { it.enabled && it.rawJson.isNotBlank() }
            if (pick != null) {
                log.i(TAG, "using Room book source: ${pick.name}")
                return pick.rawJson
            }
        }
        log.i(TAG, "falling back to built-in demo book source")
        return runCatching { SourceBootstrap.defaultLegadoSourceJson() }.getOrNull()
    }

    companion object {
        const val TAG = "LegadoEngine"
    }
}
