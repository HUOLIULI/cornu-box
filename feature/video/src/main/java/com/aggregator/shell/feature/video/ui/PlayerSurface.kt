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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.media.danmaku.DanmakuItem
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore

@Composable
fun DanmakuOverlay(
    items: List<DanmakuItem>,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(40L)
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
    val surfaceColor = MaterialTheme.colorScheme.surface
    LaunchedEffect(item) {
        try {
            player.initialize(context)
            player.prepare(item)
        } catch (e: Exception) {
            // error surfaced via player state
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = surfaceColor)
        }
        DanmakuOverlay(
            items = danmaku,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .height(180.dp)
        )
        Text(
            text = item.name.ifEmpty { item.url },
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
