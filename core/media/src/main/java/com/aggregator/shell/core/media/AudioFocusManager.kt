package com.aggregator.shell.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer

/**
 * 音乐 / 媒体模块音频焦点管理（对标 DsPlayer 后台音频处理）。
 *
 * 获得焦点时播放；失去焦点（LOSS / LOSS_TRANSIENT）时暂停并记忆，
 * 焦点恢复（GAIN）时若处于记忆暂停则自动续播。绑定到 [ExoPlayer]，
 * 由 [com.aggregator.shell.core.media.player.ExoPlayerCore] 在播放 / 释放时调用。
 */
class AudioFocusManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var player: ExoPlayer? = null
    private var suspendedByFocus = false

    fun bind(player: ExoPlayer?) {
        this.player = player
    }

    fun requestFocus() {
        val p = player ?: return
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { change -> onFocusChanged(change) }
            .build()
        focusRequest = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    fun releaseFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
        player = null
    }

    private fun onFocusChanged(change: Int) {
        val p = player ?: return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (p.isPlaying) {
                    suspendedByFocus = true
                    p.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (suspendedByFocus) {
                    suspendedByFocus = false
                    p.play()
                }
            }
            else -> {}
        }
    }
}
