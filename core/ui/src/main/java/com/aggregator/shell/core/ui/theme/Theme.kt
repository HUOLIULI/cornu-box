package com.aggregator.shell.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * PeekPro 风格「黑金」配色：近黑背景 + 金色主色 + 圆角卡片。
 * 全 App 统一暗色基准（对齐 PeekPro 观感），不随系统切浅色。
 */
val PeekBlack = Color(0xFF0B0B0F)
val PeekSurface = Color(0xFF15151B)
val PeekSurfaceVariant = Color(0xFF1F1F27)
val PeekGold = Color(0xFFE8C547)
val PeekGoldDim = Color(0xFFB79A2E)
val PeekOnSurface = Color(0xFFF4F1E8)
val PeekOnSurfaceMuted = Color(0xFFB9B5A8)

private val PeekColors = darkColorScheme(
    primary = PeekGold,
    onPrimary = PeekBlack,
    secondary = Color(0xFFC9A83B),
    onSecondary = PeekBlack,
    tertiary = Color(0xFF7C6A1E),
    background = PeekBlack,
    onBackground = PeekOnSurface,
    surface = PeekSurface,
    onSurface = PeekOnSurface,
    surfaceVariant = PeekSurfaceVariant,
    onSurfaceVariant = PeekOnSurfaceMuted,
    outline = Color(0xFF3A3A44),
    error = Color(0xFFCF667A),
    onError = Color(0xFFFFF5F5)
)

/** 黑金卡片圆角（PeekPro 观感：大圆角）。 */
val PeekRadius = androidx.compose.ui.unit.Dp(18f)

@Composable
fun AppTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PeekColors,
        content = content
    )
}
