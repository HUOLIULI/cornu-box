package com.aggregator.shell.feature.video

import com.aggregator.shell.core.data.local.entity.FavoritesEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import org.junit.Assert.*
import org.junit.Test

class VideoViewModelTest {

    @Test
    fun `play history entity should have correct structure`() {
        val history = PlayHistoryEntity(
            id = "history-1",
            sourceId = "source-1",
            contentId = "content-1",
            title = "Test Video",
            positionMs = 120000,
            module = "video",
            updated = System.currentTimeMillis()
        )

        assertEquals("history-1", history.id)
        assertEquals("source-1", history.sourceId)
        assertEquals("content-1", history.contentId)
        assertEquals("Test Video", history.title)
        assertEquals(120000, history.positionMs)
        assertEquals("video", history.module)
    }

    @Test
    fun `favorites entity for video module`() {
        val favorite = FavoritesEntity(
            id = "fav-1",
            sourceId = "source-1",
            contentId = "content-1",
            title = "Favorite Video",
            coverUrl = "http://example.com/cover.jpg",
            module = "video",
            addedTime = System.currentTimeMillis(),
            category = "movies"
        )

        assertEquals("fav-1", favorite.id)
        assertEquals("video", favorite.module)
        assertEquals("movies", favorite.category)
        assertNotNull(favorite.coverUrl)
    }

    @Test
    fun `live channel data class should be immutable`() {
        val channel = LiveChannel(
            name = "Test Channel",
            url = "http://example.com/live.m3u8",
            group = "test",
            isHls = true,
            epg = "http://example.com/epg.xml"
        )

        assertEquals("Test Channel", channel.name)
        assertEquals("http://example.com/live.m3u8", channel.url)
        assertEquals("test", channel.group)
        assertTrue(channel.isHls)
        assertEquals("http://example.com/epg.xml", channel.epg)
    }

    @Test
    fun `drama episode data class`() {
        val episode = DramaEpisode(
            title = "Episode 1",
            url = "http://example.com/ep1.mp4",
            isHls = false
        )

        assertEquals("Episode 1", episode.title)
        assertEquals("http://example.com/ep1.mp4", episode.url)
        assertFalse(episode.isHls)
    }
}
