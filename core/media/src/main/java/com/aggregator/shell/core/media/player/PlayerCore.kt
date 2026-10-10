package com.aggregator.shell.core.media.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.aggregator.shell.core.common.AppException
import com.aggregator.shell.core.media.AudioFocusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** 单次媒体自然播放结束事件，供阅读连续朗读 / 影视自动连播订阅。 */
    val playbackEnded: SharedFlow<Unit>
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

    /** 暴露底层 [ExoPlayer] 供 [MediaSession] 绑定媒体控制（锁屏 / 通知栏）。 */
    fun sessionPlayer(): androidx.media3.common.Player?
}

class ExoPlayerCore @javax.inject.Inject constructor() : PlayerCore {

    private var player: ExoPlayer? = null
    private var httpFactory: DefaultHttpDataSource.Factory? = null
    private var pendingView: PlayerView? = null
    private var audioFocus: AudioFocusManager? = null
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

    private val _playbackEnded = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val playbackEnded: SharedFlow<Unit> = _playbackEnded.asSharedFlow()

    override fun initialize(context: Context) {
        if (player == null) {
            val http = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("MediaShell/1.6")
            httpFactory = http
            val mediaSourceFactory = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, http)
            )
            player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .also { p ->
                    audioFocus = AudioFocusManager(context)
                    audioFocus?.bind(p)
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
                        if (playbackState == Player.STATE_ENDED) {
                            _playbackEnded.tryEmit(Unit)
                        }
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

    override fun sessionPlayer(): androidx.media3.common.Player? = player

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
            applyHeaders(item)
            audioFocus?.requestFocus()
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
            applyHeaders(item)
            audioFocus?.requestFocus()
            it.setMediaItem(MediaItem.fromUri(item.url))
            it.prepare()
            it.playWhenReady = true
        }
        _state.value = PlayerState.Loading
    }

    /** 把当前媒体项的自定义请求头（Referer / UA / Cookie 等）注入 HTTP 数据源。 */
    private fun applyHeaders(item: PlayMediaItem) {
        val f = httpFactory ?: return
        f.setDefaultRequestProperties(item.headers)
    }

    override fun pause() {
        if (player == null) return
        player?.pause()
        _state.value = PlayerState.Paused
    }

    override fun resume() {
        if (player == null) return
        audioFocus?.requestFocus()
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

    private companion object {
        const val MAX_RETRY = 5
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
        audioFocus?.releaseFocus()
        audioFocus = null
        _positionMs.value = 0L
        _state.value = PlayerState.Idle
    }

    override fun startService(context: Context) {
        // 前台服务必须用 startForegroundService（API 26+），Service 在 onCreate 内
        // startForeground，满足 5s 内提升为前台的约束；直接 startService 在后台场景会抛异常。
        androidx.core.content.ContextCompat.startForegroundService(
            context,
            Intent(context, com.aggregator.shell.core.media.MediaPlaybackService::class.java)
        )
    }

    override fun stopService(context: Context) {
        // stopService 会触发 onDestroy，Service 在 onDestroy 内 stopForeground(REMOVE)
        // 移除通知并结束前台状态，无需再额外 startService 传递停止信号。
        context.stopService(Intent(context, com.aggregator.shell.core.media.MediaPlaybackService::class.java))
    }

    private fun scheduleRetry() {
        val cur = current ?: return
        if (retryCount >= MAX_RETRY) return
        val attempt = retryCount
        retryJob?.cancel()
        retryJob = scope.launch {
            // PeekPro 风格：直播窗口掉出/DASH 回退时按线路退避重试
            val delay = if (PlaybackResilience.liveWindowRetryHint(cur.url, attempt)) {
                800L
            } else {
                LineRetryPolicy.nextDelayMs(attempt)
            }
            delay(delay)
            // 若期间已切换线路/媒体项，放弃本次重试，避免用旧 URL 覆盖新线路。
            if (current !== cur) return@launch
            retryCount = attempt + 1
            runCatching { prepare(cur) }
                .onFailure { _state.value = PlayerState.Error }
        }
    }
}
