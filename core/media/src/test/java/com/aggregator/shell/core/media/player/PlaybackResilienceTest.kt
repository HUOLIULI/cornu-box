package com.aggregator.shell.core.media.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResilienceTest {

    @Test
    fun `live stream urls detected as live`() {
        assertTrue(PlaybackResilience.isLiveLike("https://x/live/stream.m3u8"))
        assertTrue(PlaybackResilience.isLiveLike("https://x/hls_1/ts.ts"))
        assertTrue(PlaybackResilience.isLiveLike("https://x/vod/live=1"))
        assertTrue(PlaybackResilience.isLiveLike("https://x/vod/is-live=true"))
        assertFalse(PlaybackResilience.isLiveLike("https://x/vod/1.mp4"))
        assertFalse(PlaybackResilience.isLiveLike("https://x/1.mp4"))
    }

    @Test
    fun `html body detected as webpage`() {
        assertTrue(PlaybackResilience.looksLikeHtml("<!DOCTYPE html><html>"))
        assertTrue(PlaybackResilience.looksLikeHtml("<body>hi</body>"))
        assertTrue(PlaybackResilience.looksLikeHtml("Content-Type: text/html"))
        assertFalse(PlaybackResilience.looksLikeHtml("binary-bytes-here"))
        assertFalse(PlaybackResilience.looksLikeHtml(""))
    }

    @Test
    fun `live window retry hint only for first attempts on live urls`() {
        assertTrue(PlaybackResilience.liveWindowRetryHint("https://x/live.m3u8", 1))
        assertTrue(PlaybackResilience.liveWindowRetryHint("https://x/live.m3u8", 3))
        assertFalse(PlaybackResilience.liveWindowRetryHint("https://x/live.m3u8", 4))
        assertFalse(PlaybackResilience.liveWindowRetryHint("https://x/1.mp4", 1))
        assertFalse(PlaybackResilience.liveWindowRetryHint("https://x/1.mp4", 0))
    }

    @Test
    fun `dash falls back to progressive on fail`() {
        assertTrue(PlaybackResilience.preferProgressiveOnFail("https://x/dash.mpd", true))
        assertFalse(PlaybackResilience.preferProgressiveOnFail("https://x/dash.mpd", false))
        assertFalse(PlaybackResilience.preferProgressiveOnFail("https://x/a.m3u8", true))
    }

    @Test
    fun `line retry backoff doubles and caps`() {
        assertEquals(1000L, LineRetryPolicy.nextDelayMs(0))
        assertEquals(2000L, LineRetryPolicy.nextDelayMs(1))
        assertEquals(4000L, LineRetryPolicy.nextDelayMs(2))
        assertEquals(8000L, LineRetryPolicy.nextDelayMs(3))
        // 15s cap
        assertEquals(15000L, LineRetryPolicy.nextDelayMs(20))
        assertEquals(true, LineRetryPolicy.shouldRetry(0, 5))
        assertEquals(false, LineRetryPolicy.shouldRetry(5, 5))
    }

    @Test
    fun `ssl bypass client builds an okhttp client`() {
        val client = SslBypassClient.build()
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertTrue(client.retryOnConnectionFailure)
    }
}
