package com.aggregator.shell.core.media.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp

/**
 * 弹幕特效样式（聚合自 PeekPro 原生渲染弹幕：普通滚动 + 爱心流光/徽章/气泡）。
 */
enum class DanmakuEffect {
    NORMAL,     // 普通滚动
    HEART,      // 爱心流光
    BADGE,      // 顶部徽章
    BUBBLE      // 气泡
}

/** 扩展弹幕条目，携带特效；与现有 [DanmakuItem] 并存，老消费方不受影响。 */
data class StyledDanmaku(
    val base: DanmakuItem,
    val effect: DanmakuEffect = DanmakuEffect.NORMAL
)

/**
 * 轻量弹幕层：用 Canvas 直接绘制，走原生渲染链路（对应 PeekPro 把高频弹幕
 * 从 Flutter 绘制换成原生渲染的优化），不依赖 DanmakuFlameMaster native 库。
 *
 * @param items 时间轴上当前要显示的弹幕
 * @param progressFraction 0..1 滚动进度，由外层动画驱动
 */
@Composable
fun DanmakuLayer(
    items: List<StyledDanmaku>,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth()) {
        val width = size.width
        items.forEachIndexed { i, styled ->
            val base = styled.base
            val fontSize = base.fontSize.sp.toPx()
            val x = width - (width * progressFraction) - (i * 120f)
            drawIntoCanvas { c ->
                val paint = android.graphics.Paint().apply {
                    color = when (styled.effect) {
                        DanmakuEffect.HEART -> android.graphics.Color.parseColor("#FF6B9D")
                        DanmakuEffect.BADGE -> android.graphics.Color.parseColor("#FFB300")
                        DanmakuEffect.BUBBLE -> android.graphics.Color.parseColor("#4FC3F7")
                        else -> base.color
                    }
                    textSize = fontSize
                    isAntiAlias = true
                }
                val y = 40f + (i % 6) * (fontSize + 12f)
                val prefix = when (styled.effect) {
                    DanmakuEffect.HEART -> "❤ "
                    DanmakuEffect.BADGE -> "[徽章] "
                    DanmakuEffect.BUBBLE -> "💬 "
                    DanmakuEffect.NORMAL -> ""
                }
                c.nativeCanvas.drawText(prefix + base.text, x, y, paint)
            }
        }
    }
}
