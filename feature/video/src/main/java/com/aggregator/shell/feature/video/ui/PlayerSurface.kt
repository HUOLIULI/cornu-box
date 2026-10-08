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
    val density = androidx.compose.ui.platform.LocalDensity.current
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

@Composable
fun PlayerSurface(
    item: PlayMediaItem,
    player: PlayerCore,
    danmaku: List<DanmakuItem>
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
        // 真实视频渲染：PlayerView 绑定 ExoPlayer 输出
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
        Text(
            text = item.name.ifEmpty { item.url },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
