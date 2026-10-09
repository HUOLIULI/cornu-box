package com.aggregator.shell.core.media.player

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

enum class PlayerState { Idle, Loading, Ready, Paused, Error }

data class PlayMediaItem(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val name: String = "",
    val isHls: Boolean = url.endsWith(".m3u8", true),
    val seekPositionMs: Long = 0L
)

interface PlayerCore {
    val state: StateFlow<PlayerState>
    /** 播放位置（毫秒）的实时流，供进度条 / 歌词同步等 UI 订阅。 */
    val positionMs: StateFlow<Long>
    fun initialize(context: Context)
    fun startService(context: Context)
    fun stopService(context: Context)
    suspend fun prepare(item: PlayMediaItem)
    fun switchUrl(item: PlayMediaItem)
    fun pause()
    fun resume()
    fun release()

    /** 读取当前播放位置（毫秒），用于断点续播。 */
    fun currentPositionMs(): Long

    /** 立即定位到指定位置（毫秒）。 */
    fun seekTo(positionMs: Long)

    /** 将 ExoPlayer 输出绑定到 PlayerView（视频画面渲染入口）。 */
    fun attachPlayerView(view: PlayerView)

    /** 解除绑定。 */
    fun detachPlayerView()
}

class ExoPlayerCore @javax.inject.Inject constructor() : PlayerCore {

    private var player: ExoPlayer? = null
    private var pendingView: PlayerView? = null
    private var current: PlayMediaItem? = null
    private var pendingSeekMs: Long = 0L
    private var seekApplied = false
    private var retryCount = 0
    private var retryJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private var positionTicker: Job? = null

    override fun initialize(context: Context) {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().also { p ->
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = PlayerState.Error
                        scheduleRetry()
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        applyPendingSeek()
                    }

                    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                        applyPendingSeek()
                    }
                })
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
        pendingView?.let { it.player = null }
        pendingView = null
    }

    override suspend fun prepare(item: PlayMediaItem) {
        if (item.url.isBlank() || !PlayUrlValidator.validate(item.url)) {
            _state.value = PlayerState.Error
            throw AppException.PlayUrlInvalidException(item.url)
        }
        retryCount = 0
        current = item
        pendingSeekMs = item.seekPositionMs
        seekApplied = false
        withContext(Dispatchers.Main) {
            val p = player ?: throw IllegalStateException("Player not initialized")
            p.setMediaItem(MediaItem.fromUri(item.url))
            p.prepare()
            p.playWhenReady = true
            _state.value = PlayerState.Ready
            applyPendingSeek()
            startPositionTicker()
        }
    }

    override fun switchUrl(item: PlayMediaItem) {
        if (item.url.isBlank() || !PlayUrlValidator.validate(item.url)) {
            _state.value = PlayerState.Error
            return
        }
        current = item
        retryCount = 0
        pendingSeekMs = item.seekPositionMs
        seekApplied = false
        // release() 后 player 为 null：先重建再切，避免「状态置 Loading 但无媒体项」的不一致。
        if (player == null) return
        player?.let {
            it.setMediaItem(MediaItem.fromUri(item.url))
            it.prepare()
            it.playWhenReady = true
        }
        _state.value = PlayerState.Loading
    }

    override fun pause() {
        if (player == null) return
        player?.pause()
        _state.value = PlayerState.Paused
    }

    override fun resume() {
        if (player == null) return
        player?.play()
        _state.value = PlayerState.Ready
    }

    override fun currentPositionMs(): Long =
        player?.currentPosition ?: 0L

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    private fun applyPendingSeek() {
        if (seekApplied) return
        if (pendingSeekMs <= 0L) {
            seekApplied = true
            return
        }
        val p = player ?: return
        if (p.playbackState != Player.STATE_READY) return
        p.seekTo(pendingSeekMs)
        seekApplied = true
    }

    private fun startPositionTicker() {
        positionTicker?.cancel()
        positionTicker = scope.launch {
            while (isActive) {
                _positionMs.value = player?.currentPosition ?: 0L
                delay(250L)
            }
        }
    }

    override fun release() {
        // 幂等：已 release 时不重复 cancel scope（避免 scheduleRetry 内的 scope.launch 抛异常）
        scope.cancel()
        positionTicker?.cancel()
        retryJob?.cancel()
        player?.release()
        player = null
        pendingView?.let { it.player = null }
        pendingView = null
        current = null
        pendingSeekMs = 0L
        seekApplied = false
        retryCount = 0
        _positionMs.value = 0L
        _state.value = PlayerState.Idle
    }

    override fun startService(context: Context) {
        context.startService(Intent(context, com.aggregator.shell.core.media.MediaPlaybackService::class.java))
    }

    override fun stopService(context: Context) {
        // 前台服务需 stopForeground 才能及时移除通知栏条目，单纯 stopService 在部分
        // 机型不会立即销毁 startForeground 服务。这里发一个显式 Intent 让 Service
        // 在 onStartCommand 里 stopForeground + stopSelf 真正终结。
        context.startService(
            Intent(context, com.aggregator.shell.core.media.MediaPlaybackService::class.java)
                .putExtra(com.aggregator.shell.core.media.MediaPlaybackService.EXTRA_STOP_FOREGROUND, true)
        )
        context.stopService(Intent(context, com.aggregator.shell.core.media.MediaPlaybackService::class.java))
    }

    private fun scheduleRetry() {
        if (retryCount >= 5) return
        val cur = current ?: return
        retryJob?.cancel()
        retryJob = scope.launch {
            val backoff = minOf(2.0.pow(retryCount).toInt(), 15) * 1000L
            delay(backoff.toLong())
            retryCount++
            runCatching { prepare(cur) }
                .onFailure { _state.value = PlayerState.Error }
        }
    }
}
