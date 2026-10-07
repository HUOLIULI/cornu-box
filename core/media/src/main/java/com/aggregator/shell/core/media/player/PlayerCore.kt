package com.aggregator.shell.core.media.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aggregator.shell.core.common.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

enum class PlayerState { Idle, Loading, Ready, Paused, Error }

data class PlayMediaItem(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val name: String = "",
    val isHls: Boolean = url.endsWith(".m3u8", true)
)

interface PlayerCore {
    val state: StateFlow<PlayerState>
    fun initialize(context: Context)
    suspend fun prepare(item: PlayMediaItem)
    fun switchUrl(item: PlayMediaItem)
    fun pause()
    fun resume()
    fun release()
}

class ExoPlayerCore @javax.inject.Inject constructor() : PlayerCore {

    private var player: ExoPlayer? = null
    private var current: PlayMediaItem? = null
    private var retryCount = 0
    private var retryJob: Job? = null
    private val _state = MutableStateFlow(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override fun initialize(context: Context) {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    _state.value = PlayerState.Error
                    scheduleRetry()
                }
            })
        }
    }

    override suspend fun prepare(item: PlayMediaItem) {
        if (item.url.isBlank() || !PlayUrlValidator.validate(item.url)) {
            throw AppException.PlayUrlInvalidException(item.url)
        }
        retryCount = 0
        current = item
        withContext(Dispatchers.Main) {
            val p = player ?: throw IllegalStateException("Player not initialized")
            p.setMediaItem(MediaItem.fromUri(item.url))
            p.prepare()
            p.playWhenReady = true
            _state.value = PlayerState.Ready
        }
    }

    override fun switchUrl(item: PlayMediaItem) {
        current = item
        retryCount = 0
        player?.let {
            it.setMediaItem(MediaItem.fromUri(item.url))
            it.prepare()
            it.playWhenReady = true
        }
        _state.value = PlayerState.Loading
    }

    override fun pause() {
        player?.pause()
        _state.value = PlayerState.Paused
    }

    override fun resume() {
        player?.play()
        _state.value = PlayerState.Ready
    }

    override fun release() {
        player?.release()
        player = null
        _state.value = PlayerState.Idle
    }

    private fun scheduleRetry() {
        if (retryCount >= 5) return
        val cur = current ?: return
        retryJob?.cancel()
        retryJob = GlobalScope.launch {
            val backoff = minOf(2.0.pow(retryCount).toInt(), 15) * 1000L
            delay(backoff.toLong())
            retryCount++
            runCatching { prepare(cur) }
                .onFailure { _state.value = PlayerState.Error }
        }
    }
}
