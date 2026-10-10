package com.aggregator.shell.core.media.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aggregator.shell.core.common.AppException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    /** 获取当前播放位置（毫秒） */
    fun getCurrentPositionMs(): Long

    /** 跳转到指定位置 */
    fun seekTo(positionMs: Long)

    /** 设置播放速度 */
    fun setPlaybackSpeed(speed: Float)

    /** 获取播放速度 */
    fun getPlaybackSpeed(): Float

    /** 获取媒体时长 */
    fun getDurationMs(): Long

    /** 是否正在播放 */
    fun isPlaying(): Boolean

    /** 将 ExoPlayer 输出绑定到 PlayerView（视频画面渲染入口）。 */
    fun attachPlayerView(view: PlayerView)

    /** 解除绑定。 */
    fun detachPlayerView()

    /** 设置播放完成回调。 */
    fun setOnCompletionListener(listener: (() -> Unit)?)

    /** 设置播放错误回调（播放失败时触发，用于多线路自动切换）。 */
    fun setOnErrorListener(listener: ((androidx.media3.common.PlaybackException) -> Unit)?)
}

class ExoPlayerCore @javax.inject.Inject constructor() : PlayerCore {

    private var player: ExoPlayer? = null
    private var pendingView: PlayerView? = null
    private var current: PlayMediaItem? = null
    private var retryCount = 0
    private var retryJob: Job? = null
    private var currentPositionMs: Long = 0L
    private var currentSpeed: Float = 1.0f
    private var completionListener: (() -> Unit)? = null
    private var completionListenerAttached: Player.Listener? = null
    private var errorListener: ((PlaybackException) -> Unit)? = null
    private var errorListenerAttached: Player.Listener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override fun initialize(context: Context) {
        if (player == null) {
            player = ExoPlayer.Builder(context)
                .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                .build().also { p ->
                    p.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            _state.value = PlayerState.Error
                            errorListener?.invoke(error)
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            currentPositionMs = newPosition.positionMs
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                _state.value = PlayerState.Ready
                            } else {
                                _state.value = PlayerState.Paused
                            }
                        }
                    })
                    // 若 setOnCompletionListener 在 initialize 之前被调用，统一回填
                    completionListenerAttached?.let { p.removeListener(it) }
                    completionListenerAttached = if (completionListener == null) null else object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                completionListener?.invoke()
                            }
                        }
                    }.also { p.addListener(it) }
                }
            // 若 PlayerView 先于初始化挂载，在此回填输出
            pendingView?.let { view ->
                player?.let { view.player = it }
                pendingView = null
            }
        }
    }

    override fun attachPlayerView(view: PlayerView) {
        val p = player
        if (p != null) {
            view.player = p
            view.useController = true
        } else {
            pendingView = view
        }
    }

    override fun detachPlayerView() {
        pendingView = null
    }

    override fun setOnCompletionListener(listener: (() -> Unit)?) {
        val p = player
        if (p != null) {
            completionListenerAttached?.let { p.removeListener(it) }
            completionListener = listener
            completionListenerAttached = if (listener == null) null else object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        completionListener?.invoke()
                    }
                }
            }.also { p.addListener(it) }
        } else {
            // player 尚未初始化，记录待挂载；initialize 时统一回填
            completionListener = listener
            completionListenerAttached = null
        }
    }

    override fun setOnErrorListener(listener: ((PlaybackException) -> Unit)?) {
        val p = player
        if (p != null) {
            errorListenerAttached?.let { p.removeListener(it) }
            errorListener = listener
            errorListenerAttached = if (listener == null) null else object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    errorListener?.invoke(error)
                }
            }.also { p.addListener(it) }
        } else {
            errorListener = listener
            errorListenerAttached = null
        }
    }

    override suspend fun prepare(item: PlayMediaItem) {
        if (item.url.isBlank() || !PlayUrlValidator.validate(item.url)) {
            throw AppException.PlayUrlInvalidException(item.url)
        }
        retryCount = 0
        current = item
        currentPositionMs = 0L
        withContext(Dispatchers.Main) {
            val p = player ?: throw IllegalStateException("Player not initialized")
            p.setMediaItem(MediaItem.fromUri(item.url))
            p.playbackParameters = androidx.media3.common.PlaybackParameters(
                currentSpeed, 1.0f
            )
            p.prepare()
            p.playWhenReady = true
            _state.value = PlayerState.Loading
        }
    }

    override fun switchUrl(item: PlayMediaItem) {
        if (item.url.isBlank() || !PlayUrlValidator.validate(item.url)) {
            _state.value = PlayerState.Error
            return
        }
        current = item
        retryCount = 0
        currentPositionMs = 0L
        player?.let {
            it.setMediaItem(MediaItem.fromUri(item.url))
            it.prepare()
            it.playWhenReady = true
        }
        _state.value = PlayerState.Loading
    }

    override fun pause() {
        currentPositionMs = getCurrentPositionMs()
        player?.pause()
        _state.value = PlayerState.Paused
    }

    override fun resume() {
        player?.play()
        _state.value = PlayerState.Ready
    }

    override fun release() {
        scope.cancel()
        currentPositionMs = 0L
        player?.release()
        player = null
        pendingView = null
        _state.value = PlayerState.Idle
    }

    override fun getCurrentPositionMs(): Long {
        return player?.currentPosition ?: currentPositionMs
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        player?.let {
            it.playbackParameters = androidx.media3.common.PlaybackParameters(speed, 1.0f)
        }
    }

    override fun getPlaybackSpeed(): Float {
        return currentSpeed
    }

    override fun getDurationMs(): Long {
        return player?.duration ?: 0L
    }

    override fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    private fun scheduleRetry() {
        if (retryCount >= 5) return
        val cur = current ?: return
        retryJob?.cancel()
        retryJob = scope.launch {
            val backoff = minOf(2.0.pow(retryCount).toInt(), 15) * 1000L
            delay(backoff)
            retryCount++
            runCatching { prepare(cur) }
                .onFailure { _state.value = PlayerState.Error }
        }
    }
}
