package com.aggregator.shell.core.source

import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.search.AggregateVideoSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregateVideoSearchTest {

    private class FakeEngine(
        private val results: List<VideoResult>,
        private val delayMs: Long = 0
    ) : VideoEngine {
        override suspend fun search(keyword: String, page: Int): List<VideoResult> {
            if (delayMs > 0) delay(delayMs)
            return results
        }
        override suspend fun getDetail(id: String) =
            throw UnsupportedOperationException()
        override suspend fun getPlayUrl(id: String, flag: String) =
            throw UnsupportedOperationException()
    }

    private fun video(id: String, title: String, sourceKey: String) =
        VideoResult(id = id, title = title, coverUrl = "https://x/$id", sourceKey = sourceKey)

    @Test
    fun `dedupes by title and sourceKey keeping first hit`() = runBlocking {
        val a = FakeEngine(listOf(video("1", "英雄", "src-a"), video("2", "少年", "src-a")))
        val b = FakeEngine(listOf(video("3", "英雄", "src-b"), video("1", "英雄", "src-a")))
        val search = AggregateVideoSearch(listOf(a, b))
        val result = search.search("英雄")
        assertEquals(2, result.size)
        assertEquals(listOf(video("1", "英雄", "src-a"), video("2", "少年", "src-a")),
            result.map { it.result })
        val hero = result.first { it.result.title == "英雄" }
        assertEquals(listOf("src-a", "src-b"), hero.sourceKeys)
    }

    @Test
    fun `keyword hit titles sort first`() = runBlocking {
        val engine = FakeEngine(
            listOf(
                video("1", "无关影片", "s1"),
                video("2", "英雄传说", "s1"),
                video("3", "英雄本色", "s2")
            )
        )
        val result = AggregateVideoSearch(listOf(engine)).search("英雄")
        assertEquals(listOf("英雄传说", "英雄本色", "无关影片"), result.map { it.result.title })
    }

    @Test
    fun `more source hits sort ahead`() = runBlocking {
        val a = FakeEngine(listOf(video("1", "A 片", "s1"), video("2", "B 片", "s1")))
        val b = FakeEngine(listOf(video("1", "A 片", "s2"), video("2", "B 片", "s2")))
        val c = FakeEngine(listOf(video("3", "C 片", "s3")))
        val result = AggregateVideoSearch(listOf(a, b, c)).search("片")
        val ranks = result.associate { it.result.title to it.sourceKeys.size }
        assertEquals(mapOf("A 片" to 2, "B 片" to 2, "C 片" to 1), ranks)
        assertEquals(listOf("A 片", "B 片", "C 片"), result.map { it.result.title })
    }

    @Test
    fun `engine failures do not break aggregate result`() = runBlocking {
        class BrokenEngine : VideoEngine {
            override suspend fun search(keyword: String, page: Int): List<VideoResult> =
                throw java.io.IOException("down")
            override suspend fun getDetail(id: String) =
                throw UnsupportedOperationException()
            override suspend fun getPlayUrl(id: String, flag: String) =
                throw UnsupportedOperationException()
        }
        val ok = FakeEngine(listOf(video("1", "好的", "ok")))
        val result = AggregateVideoSearch(listOf(BrokenEngine(), ok)).search("x")
        assertEquals(listOf("好的"), result.map { it.result.title })
    }

    @Test
    fun `empty engines returns empty list`() = runBlocking {
        val search = AggregateVideoSearch(emptyList())
        val result = search.search("x")
        assertEquals(emptyList(), result)
    }

    @Test
    fun `blank keyword sorts by source count only`() = runBlocking {
        val a = FakeEngine(listOf(video("1", "A 片", "s1")))
        val b = FakeEngine(listOf(video("2", "B 片", "s2")))
        val c = FakeEngine(listOf(video("3", "B 片", "s3")))
        val result = AggregateVideoSearch(listOf(a, b, c)).search("   ")
        // 空 keyword 时退化为纯源数量排序，B 片命中 2 源排前
        assertEquals(listOf("B 片", "A 片"), result.map { it.result.title }.distinct())
    }

    @Test
    fun `blank title items are filtered out`() = runBlocking {
        val engine = FakeEngine(
            listOf(video("1", "  ", "s1"), video("2", "正常", "s1"))
        )
        val result = AggregateVideoSearch(listOf(engine)).search("正常")
        assertEquals(listOf("正常"), result.map { it.result.title })
    }

    @Test
    fun `concurrent search runs all engines in parallel`() {
        val engines = (1..4).map { i ->
            FakeEngine(listOf(video("$i", "t$i", "s$i")), delayMs = 300)
        }
        val search = AggregateVideoSearch(engines)
        val start = System.currentTimeMillis()
        runBlocking(Dispatchers.Default) {
            val result = search.search("t")
            assertEquals(4, result.size)
        }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("expected parallel < 600ms, got $elapsed", elapsed < 600)
    }
}
