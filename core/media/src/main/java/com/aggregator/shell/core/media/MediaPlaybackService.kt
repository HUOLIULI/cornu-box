package com.aggregator.shell.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.media.player.PlayerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 媒体播放前台服务（影视 / 音乐 / 朗读共用 [PlayerCore]）。
 *
 * 对标 PeekPro 锁屏媒体通知：前台通知带 播放/暂停、停止 媒体按钮，
 * 音乐模块音频焦点由 [AudioFocusManager] 在 [PlayerCore] 内处理
 * （失焦暂停、复焦续播，对标 DsPlayer 后台音频处理）。
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    @Inject
    lateinit var playerCore: PlayerCore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "media_playback_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        stateJob = scope.launch {
            playerCore.state.collectLatest { state ->
                updateNotification(state)
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification(playerCore.state.value))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playerCore.let {
                if (it.state.value == PlayerState.Paused) it.resume() else it.pause()
            }
            ACTION_STOP -> {
                playerCore.pause()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // 先移除前台通知再结束，避免部分机型残留通知栏条目。
        stopForeground(STOP_FOREGROUND_REMOVE)
        stateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "媒体播放",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: PlayerState): Notification {
        val text = when (state) {
            PlayerState.Ready -> "播放中"
            PlayerState.Paused -> "已暂停"
            PlayerState.Loading -> "缓冲中…"
            else -> "已停止"
        }
        val playing = state == PlayerState.Ready
        val tapIntent = PendingIntent.getService(
            this, 0, Intent(this, MediaPlaybackService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPause = mediaActionPendingIntent(ACTION_PLAY_PAUSE)
        val stop = mediaActionPendingIntent(ACTION_STOP)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("聚合壳 · 媒体播放")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(playing)
            .setContentIntent(tapIntent)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                if (playing) "暂停" else "播放",
                playPause
            )
            .addAction(0, "停止", stop)
            .build()
    }

    private fun mediaActionPendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, MediaPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updateNotification(state: PlayerState) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.aggregator.shell.action_play_pause"
        const val ACTION_STOP = "com.aggregator.shell.action_stop"
    }
}
