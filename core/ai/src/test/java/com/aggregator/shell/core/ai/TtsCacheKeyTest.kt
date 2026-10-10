package com.aggregator.shell.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TtsCacheKeyTest {

    @Test
    fun `cache key is deterministic for same model voice text`() {
        val a = TtsCacheKey.keyOf("tts-1", "alloy", "第一章")
        val b = TtsCacheKey.keyOf("tts-1", "alloy", "第一章")
        assertEquals(a, b)
    }

    @Test
    fun `cache key changes when voice differs`() {
        val a = TtsCacheKey.keyOf("tts-1", "alloy", "第一章")
        val b = TtsCacheKey.keyOf("tts-1", "echo", "第一章")
        assertNotEquals(a, b)
    }

    @Test
    fun `cache key changes when model differs`() {
        val a = TtsCacheKey.keyOf("tts-1", "alloy", "第一章")
        val b = TtsCacheKey.keyOf("tts-1-hd", "alloy", "第一章")
        assertNotEquals(a, b)
    }

    @Test
    fun `cache key changes when text differs`() {
        val a = TtsCacheKey.keyOf("tts-1", "alloy", "第一章")
        val b = TtsCacheKey.keyOf("tts-1", "alloy", "第二章")
        assertNotEquals(a, b)
    }

    @Test
    fun `cache key is 40 hex chars`() {
        val key = TtsCacheKey.keyOf("tts-1", "alloy", "abc")
        assertEquals(40, key.length)
        key.forEach { c -> c in "0123456789abcdef" }
    }
}
