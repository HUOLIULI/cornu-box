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
        startForeground(NOTIFICATION_ID, buildNotification("正在播放"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

    private fun buildNotification(text: String): Notification {
        val tapIntent = Intent(this, MediaPlaybackService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("聚合壳 · 媒体播放")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(state: PlayerState) {
        val text = when (state) {
            PlayerState.Idle -> "已停止"
            PlayerState.Loading -> "缓冲中…"
            PlayerState.Ready -> "播放中"
            PlayerState.Paused -> "已暂停"
            PlayerState.Error -> "播放出错"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
