package com.aggregator.shell.feature.video.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.media.player.PlayerCore
import kotlinx.coroutines.delay

/**
 * 视频手势层：覆盖在播放器上方。
 * 交互：
 *  - 双击：右半屏快进 15s，左半屏快退 15s
 *  - 长按 300ms：循环切换倍速 1x -> 1.5x -> 2x -> 1x
 * detectTapGestures 不拦截单指拖动，底层 PlayerView 控制栏仍可操作。
 */
@Composable
fun VideoGestureOverlay(
    player: PlayerCore,
    modifier: Modifier = Modifier
) {
    var toastVisible by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }
    val toastAlpha by animateFloatAsState(
        targetValue = if (toastVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "toastAlpha"
    )

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val isForward = offset.x > size.width / 2f
                            val current = player.getCurrentPositionMs()
                            if (isForward) {
                                val target = (current + 15_000L)
                                    .coerceIn(0L, player.getDurationMs().coerceAtLeast(0L))
                                player.seekTo(target)
                                toastText = "快进 15 秒"
                                toastVisible = true
                            } else {
                                player.seekTo((current - 15_000L).coerceAtLeast(0L))
                                toastText = "快退 15 秒"
                                toastVisible = true
                            }
                        },
                        onLongPress = { _ ->
                            val speed = player.getPlaybackSpeed()
                            val newSpeed = when {
                                speed < 1.5f -> 1.5f
                                speed < 2.0f -> 2.0f
                                else -> 1.0f
                            }
                            player.setPlaybackSpeed(newSpeed)
                            val label = when (newSpeed) {
                                1.0f -> "1x"
                                1.5f -> "1.5x"
                                else -> "2x"
                            }
                            toastText = "倍速 $label"
                            toastVisible = true
                        }
                    )
                }
        )

        if (toastVisible || toastAlpha > 0f) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .graphicsLayer(alpha = toastAlpha)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = toastText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    LaunchedEffect(toastVisible) {
        if (toastVisible) {
            delay(1_500L)
            toastVisible = false
        }
    }
}
