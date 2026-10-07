package com.aggregator.shell.core.source

import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.engine.LegadoEngine
import com.aggregator.shell.core.source.parse.RuleParser
import com.aggregator.shell.core.source.sandbox.JsResult
import com.aggregator.shell.core.source.sandbox.RhinoJsExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegadoEngineTest {

    @Test
    fun `rule parser handles jsonpath`() {
        val parser = RuleParser(RhinoJsExecutor())
        val json = """{"data":{"books":[{"id":"1","title":"斗破苍穹","writer":"天蚕土豆","cover":"http://x/c.png"}]}}"""
        val result = runBlocking { parser.single(json, "$.data.books[0].title") }
        assertEquals("斗破苍穹", result)
    }

    @Test
    fun `rule parser regex extracts token`() {
        val parser = RuleParser(RhinoJsExecutor())
        val result = runBlocking { parser.single("abc-123-def", ":\\d+") }
        assertEquals("123", result)
    }

    @Test
    fun `js timeout is enforced`() = runTest {
        val js = RhinoJsExecutor()
        val r = js.execute("while(true){}", timeoutMillis = 200L)
        assert(r is JsResult.Timeout || r is JsResult.Failure)
    }
}
