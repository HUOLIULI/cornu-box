package com.aggregator.shell.core.data

import com.aggregator.shell.core.data.local.entity.FavoritesEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FavoritesDaoTest {

    // 实际测试需要 Android 环境，这里为框架测试
    @Test
    fun `favorites entity should have required fields`() {
        val favorite = FavoritesEntity(
            id = "test-id",
            sourceId = "source-1",
            contentId = "content-1",
            title = "Test Title",
            coverUrl = "http://example.com/cover.png",
            module = "video",
            addedTime = System.currentTimeMillis(),
            category = "movies"
        )

        assertEquals("test-id", favorite.id)
        assertEquals("source-1", favorite.sourceId)
        assertEquals("content-1", favorite.contentId)
        assertEquals("Test Title", favorite.title)
        assertEquals("http://example.com/cover.png", favorite.coverUrl)
        assertEquals("video", favorite.module)
        assertEquals("movies", favorite.category)
    }

    @Test
    fun `favorites entity should have default values`() {
        val favorite = FavoritesEntity(
            id = "test-id",
            sourceId = "source-1",
            contentId = "content-1",
            title = "Test Title",
            module = "reader"
        )

        assertEquals("default", favorite.category)
        assertEquals(0L, favorite.addedTime)
        assertNull(favorite.coverUrl)
    }
}
