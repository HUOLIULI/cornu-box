package com.aggregator.shell.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * PeekPro 风格媒体卡片：近黑圆角卡片 + 金色高亮，覆盖 9:16 竖屏封面占位。
 * [modifier] 由调用方给宽度/网格间距。
 */
@Composable
fun PeekMediaCard(
    title: String,
    subtitle: String,
    coverUrl: String? = null,
    onClick: () -> Unit
) {
    CardPeek(
        modifier = Modifier
            .clickable { onClick() }
            .padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            CoilCover(url = coverUrl, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 圆角卡片容器（PeekPro 黑金观感）。 */
@Composable
fun CardPeek(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        content()
    }
}

/** 金色标题行（沉浸式播放页头部）。 */
@Composable
fun PeekHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/** Coil 封面占位（URL 为空显示近黑占位块）。 */
@Composable
fun CoilCover(url: String?, modifier: Modifier = Modifier) {
    val target = url?.takeIf { it.isNotBlank() }
    if (target != null) {
        androidx.compose.foundation.layout.Box(modifier) {
            coil.compose.AsyncImage(
                model = target,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text("▢", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        }
    }
}
