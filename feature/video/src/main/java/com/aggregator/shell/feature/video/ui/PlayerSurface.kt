package com.aggregator.shell.feature.video.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.aggregator.shell.core.media.danmaku.DanmakuItem
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

@Composable
fun DanmakuOverlay(
    items: List<DanmakuItem>,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val ctx = kotlinx.coroutines.currentCoroutineContext()
        val job = ctx[kotlinx.coroutines.Job]
        while (job?.isActive == true) {
            delay(40L)
            tick = (tick + 1) % 10_000
        }
    }
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 16f * density.density
        paint.isAntiAlias = true
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val scroll = (tick * 2) % (size.width + 200f)
        items.take(12).forEachIndexed { i, item ->
            val x = size.width - scroll + i * 180f
            val y = size.height - 30f - (i % 3) * 24f
            if (x in -200f..(size.width + 200f)) {
                nativeCanvas.drawText(item.text, x, y, paint)
            }
        }
    }
}

/**
 * 片头片尾跳过：每秒检查一次播放位置，命中片头结束或片尾起点时自动 seek。
 */
@Composable
fun SkipIntroOutro(
    player: PlayerCore,
    introEndMs: Long,
    outroStartMs: Long
) {
    LaunchedEffect(player, introEndMs, outroStartMs) {
        while (true) {
            delay(1_000L)
            val pos = player.getCurrentPositionMs()
            val dur = player.getDurationMs()
            if (introEndMs > 0 && pos < introEndMs && pos > 0L) {
                player.seekTo(introEndMs)
            }
            if (outroStartMs > 0 && dur > 0 && pos >= outroStartMs && pos < dur) {
                player.seekTo(dur - 5_000L)
            }
        }
    }
}

@Composable
fun PlayerSurface(
    item: PlayMediaItem,
    player: PlayerCore,
    danmaku: List<DanmakuItem>,
    introEndMs: Long = 0L,
    outroStartMs: Long = 0L
) {
    val context = LocalContext.current

    LaunchedEffect(item) {
        runCatching {
            player.initialize(context)
            player.prepare(item)
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    controllerAutoShow = true
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> player.attachPlayerView(view) }
        )
        DanmakuOverlay(
            items = danmaku,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .height(180.dp)
        )
        SkipIntroOutro(player, introEndMs, outroStartMs)
        Text(
            text = item.name.ifEmpty { item.url },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
