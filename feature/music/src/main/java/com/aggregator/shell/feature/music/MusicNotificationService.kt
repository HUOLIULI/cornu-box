package com.aggregator.shell.feature.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.aggregator.shell.core.media.player.ExoPlayerCore
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 音乐通知栏媒体控制服务
 * 使用前台通知提供播放/暂停/上一曲/下一曲控制。
 * 通过 [MusicNotificationService.currentQueue] / [currentIndex] 管理播放队列，
 * 上一曲/下一曲通过队列索引遍历。
 */
@AndroidEntryPoint
class MusicNotificationService : Service() {

    @Inject
    lateinit var musicEngine: MusicEngine

    private var playerCore: PlayerCore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 当前正在播放的歌曲（供通知展示与 UI 同步） */
    @Volatile
    private var currentSong: MusicResult? = null

    companion object {
        const val CHANNEL_ID = "music_notification"
        const val NOTIFICATION_ID = 1

        /** 全局播放队列：上一曲/下一曲通过此列表遍历 */
        @JvmStatic
        var currentQueue: MutableList<MusicResult> = mutableListOf()

        /** 当前播放索引 */
        @JvmStatic
        var currentIndex: Int = 0

        @JvmStatic
        var isServiceActive: Boolean = false

        /** 通知栏"下一曲"回调（由 MusicActivity/ViewModel 注册） */
        @JvmStatic
        var onNextTrackListener: (() -> Unit)? = null

        /** 通知栏"上一曲"回调（由 MusicActivity/ViewModel 注册） */
        @JvmStatic
        var onPrevTrackListener: (() -> Unit)? = null

        /** 当前 ExoPlayerCore 实例（由 Service 维护，供静态方法访问） */
        @Volatile
        @JvmStatic
        var currentPlayerCore: PlayerCore? = null

        /** 播放前预设的 seek 位置（毫秒），Service prepare 完成后消费并清零 */
        @Volatile
        @JvmStatic
        var pendingSeekMs: Long = 0L

        /** 获取当前播放进度（毫秒），无播放时返回 0 */
        @JvmStatic
        fun getCurrentPositionMs(): Long {
            return currentPlayerCore?.getCurrentPositionMs() ?: 0L
        }

        /** 播放指定位置（毫秒） */
        @JvmStatic
        fun seekTo(ms: Long) {
            currentPlayerCore?.seekTo(ms)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun skipToNext() {
        if (currentIndex < currentQueue.size - 1) {
            currentIndex++
            playCurrent()
        } else {
            Toast.makeText(this, "已是最后一曲", Toast.LENGTH_SHORT).show()
        }
    }

    private fun skipToPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            playCurrent()
        }
    }

    /** 播放当前队列中的歌曲 */
    fun playCurrent() {
        val song = currentQueue.getOrNull(currentIndex) ?: return
        playSong(song)
    }

    /** 播放指定歌曲并同步通知栏（由 Service 内部或外部直接调用） */
    fun playSong(song: MusicResult) {
        currentSong = song
        scope.launch {
            try {
                val url = musicEngine.getMusicUrl(song, "320")
                val item = PlayMediaItem(url = url, name = "${song.title} - ${song.artist}")
                // 复用已有 player，避免旧 ExoPlayer 泄漏；不存在才新建
                val core = playerCore ?: ExoPlayerCore().also { playerCore = it }
                currentPlayerCore = core
                core.initialize(this@MusicNotificationService)
                core.prepare(item)
                // 消费 pending seek（断点续播）
                val seekMs = pendingSeekMs
                if (seekMs > 0L) {
                    core.seekTo(seekMs)
                    pendingSeekMs = 0L
                }
                startForeground(NOTIFICATION_ID, buildNotification(song))
                isServiceActive = true
            } catch (e: Exception) {
                Toast.makeText(this@MusicNotificationService, "播放失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 由外部通过 startService 触发播放 */
    fun startPlayback() {
        val song = currentQueue.getOrNull(currentIndex) ?: return
        playSong(song)
    }

    private fun buildNotification(song: MusicResult): Notification {
        val pendingPlay = buildPendingIntent(song, "play")
        val pendingPause = buildPendingIntent(song, "pause")
        val pendingNext = buildPendingIntent(song, "next")
        val pendingPrev = buildPendingIntent(song, "prev")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText("${song.artist} - ${song.album}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(pendingPlay)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_previous, "上一曲", pendingPrev
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause, "暂停", pendingPause
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_next, "下一曲", pendingNext
                ).build()
            )
        return builder.build()
    }

    private fun buildPendingIntent(song: MusicResult, action: String): PendingIntent {
        val intent = Intent(this, MusicNotificationService::class.java).apply {
            putExtra("ACTION", action)
            putExtra("SONG_ID", song.id)
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("ACTION")
        when (action) {
            "play", "play_now" -> {
                val current = currentQueue.getOrNull(currentIndex)
                val sameSong = current?.id == currentSong?.id
                if (!isServiceActive || current == null || !sameSong) {
                    startPlayback()
                } else {
                    playerCore?.resume()
                }
            }
            "pause" -> playerCore?.pause()
            "next" -> {
                skipToNext()
                onNextTrackListener?.invoke()
            }
            "prev" -> {
                skipToPrevious()
                onPrevTrackListener?.invoke()
            }
            else -> {
                isServiceActive = true
            }
        }
        // 首次启动时进入前台服务模式
        if (!isServiceActive) {
            isServiceActive = true
            currentSong?.let { startForeground(NOTIFICATION_ID, buildNotification(it)) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceActive = false
        scope.cancel()
        playerCore?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "音乐播放控制",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }
}
