package com.aggregator.shell.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 桌面歌词浮层服务（对标 PeekPro `DesktopLyricsService`）。
 *
 * 通过 [WindowManager] 在桌面上悬浮显示当前歌词行，随
 * [com.aggregator.shell.core.media.player.PlayerCore.positionMs] 刷新。
 * 需 `SYSTEM_ALERT_WINDOW` 权限；未授权时 `onStartCommand` 添加浮层失败会静默降级。
 * 歌词内容经 [updateLyric] 注入，本服务只做显示，不持播放器生命周期。
 */
class LyricsOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var view: View? = null
    private var titleView: TextView? = null
    private var lyricView: TextView? = null

    companion object {
        const val EXTRA_TITLE = "extra_lyric_title"
        const val EXTRA_LYRIC = "extra_lyric_line"
        const val CHANNEL_ID = "lyrics_overlay_channel"
        private const val NOTIFICATION_ID = 2001

        /** 同进程内当前运行实例，供 ViewModel 直推歌词行（跨进程才需广播）。 */
        @Volatile
        var instance: LyricsOverlayService? = null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val line = intent?.getStringExtra(EXTRA_LYRIC).orEmpty()
        showOverlay(title, line)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 由调用方（MusicViewModel / MediaPlaybackService）定期推送当前歌词行。 */
    fun updateLyric(title: String, line: String) {
        if (view == null) return
        if (Looper.getMainLooper().isCurrentThread) {
            applyText(title, line)
        } else {
            Handler(Looper.getMainLooper()).post { applyText(title, line) }
        }
    }

    private fun applyText(title: String, line: String) {
        titleView?.text = title
        lyricView?.text = line.ifEmpty { "—" }
    }

    /** 纯代码构建浮层布局，避免依赖 XML resources。 */
    private fun buildOverlayView(onClose: () -> Unit): View {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val lyricTv = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(16f), dp(12f), dp(16f), dp(4f))
        }
        val titleTv = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(dp(16f), 0, dp(44f), dp(8f))
        }
        val close = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val params = FrameLayout.LayoutParams(dp(40f), dp(40f)).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            layoutParams = params
            setOnClickListener { onClose() }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        column.addView(titleTv)
        column.addView(lyricTv)

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.argb(200, 20, 20, 20))
        }
        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(close)
        titleView = titleTv
        lyricView = lyricTv
        return root
    }

    private fun showOverlay(title: String, line: String) {
        if (view != null) return
        val v = buildOverlayView { stopSelf() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 80
        }
        try {
            windowManager?.addView(v, params)
            view = v
        } catch (_: Exception) {
            // SYSTEM_ALERT_WINDOW 未授权或添加失败，静默降级
        }
        applyText(title, line)
    }

    override fun onDestroy() {
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
        titleView = null
        lyricView = null
        instance = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("桌面歌词")
            .setContentText("正在显示歌词浮层")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
