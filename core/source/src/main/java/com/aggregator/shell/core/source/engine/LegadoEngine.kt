package com.aggregator.shell.core.source.engine

import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.Chapter
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.source.parse.RuleParser
import com.aggregator.shell.core.source.sandbox.JsSandboxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Legado-compatible book source engine.
 *
 * A Legado source is a JSON blob with `ruleSearch` / `ruleToc` / `ruleContent`
 * blocks whose values are parsed by [RuleParser]. The engine fetches the
 * search / TOC URL described by the source, applies the JSONPath rules, and
 * returns structured results.
 */
class LegadoEngine(
    private val client: OkHttpClient,
    private val jsExecutor: JsSandboxExecutor
) : ReaderEngine {

    private val parser = RuleParser(jsExecutor)

    override suspend fun search(keyword: String, page: Int): List<BookResult> {
        val sourceJson = fetchBookSourceJson() ?: return emptyList()
        val source = org.json.JSONObject(sourceJson)
        val rule = source.getJSONObject("ruleSearch")
        val searchUrl = source.optString("searchUrl")

        val body = get(searchUrl.replace("{{key}}", keyword).replace("{{page}}", page.toString()))
        return (0..3).mapNotNull { i ->
            try {
                val items = body
                BookResult(
                    id = parser.single(items, rule.optString("id")).ifEmpty { "book-$i" },
                    name = parser.single(items, rule.optString("name")),
                    author = parser.single(items, rule.optString("author")),
                    coverUrl = parser.single(items, rule.optString("coverUrl")),
                    bookUrl = parser.single(items, rule.optString("bookUrl")),
                    sourceName = source.optString("bookSourceName")
                )
            } catch (e: Exception) { null }
        }
    }

    override suspend fun getToc(bookId: String): List<Chapter> {
        val sourceJson = fetchBookSourceJson() ?: return emptyList()
        val source = org.json.JSONObject(sourceJson)
        val rule = source.getJSONObject("ruleToc")
        val bookUrl = source.optString("searchUrl").let {
            // For shell demo, bookId is expected to be the full URL.
            bookId
        }
        val body = get(bookUrl)
        val chapterItems = (0..50).mapIndexedNotNull { i, _ ->
            try {
                Chapter(
                    index = i,
                    title = parser.single(body, rule.optString("chapterName")),
                    url = parser.single(body, rule.optString("chapterUrl"))
                )
            } catch (e: Exception) { null }
        }
        return chapterItems
    }

    override suspend fun getContent(chapterId: String): String {
        val sourceJson = fetchBookSourceJson() ?: return ""
        val source = org.json.JSONObject(sourceJson)
        val rule = source.optJSONObject("ruleContent") ?: return get(chapterId)
        val body = get(chapterId)
        return parser.single(body, rule.optString("content")).let {
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

    private suspend fun fetchBookSourceJson(): String? =
        // In full version: look up from Room book_sources. For shell demo: assets.
        try {
            com.aggregator.shell.core.source.engine.SourceBootstrap.defaultLegadoSourceJson()
        } catch (e: Exception) { null }
}
