package com.aggregator.shell.feature.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.Chapter
import com.aggregator.shell.core.source.api.ReaderEngine
import kotlin.math.roundToInt
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderPageUi(
    book: BookResult,
    readerEngine: ReaderEngine,
    startIndex: Int,
    resumeChapter: String = "",
    fontSize: Float,
    lineHeight: Float,
    eyeCare: Boolean,
    onBack: (currentChapterTitle: String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onEyeCareToggle: (Boolean) -> Unit
) {
    var showToc by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { maxOf(chapters.size, 1) }
    )

    LaunchedEffect(book.id) {
        val toc = runCatching { readerEngine.getToc(book.id) }.getOrElse { emptyList() }
        chapters = toc
        if (toc.isNotEmpty()) {
            val targetIndex = if (resumeChapter.isNotBlank()) {
                toc.indexOfFirst { it.title == resumeChapter }.coerceAtLeast(startIndex)
            } else startIndex
            pagerState.scrollToPage(targetIndex.coerceIn(0, toc.size - 1))
        }
    }

    val currentChapterIndex = pagerState.currentPage

    val pageBg = if (eyeCare) Color(0xFFF5F0DC) else MaterialTheme.colorScheme.background
    val pageText = if (eyeCare) Color(0xFF5C5546) else MaterialTheme.colorScheme.onBackground
    val accent = if (eyeCare) Color(0xFFB8860B) else MaterialTheme.colorScheme.primary

    val progressAnim by animateFloatAsState(
        targetValue = if (chapters.isNotEmpty()) {
            (currentChapterIndex + 1f / chapters.size) / chapters.size
        } else 0f,
        animationSpec = tween(400),
        label = "tocProgress"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = chapters.getOrNull(currentChapterIndex)?.title
                        ?: book.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = pageText,
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = { onBack(chapters.getOrNull(currentChapterIndex)?.title ?: "") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "返回",
                        tint = pageText
                    )
                }
            },
            actions = {
                IconButton(onClick = { showToc = true }) {
                    Icon(
                        Icons.Default.FormatListNumbered,
                        contentDescription = "目录",
                        tint = pageText
                    )
                }
                IconButton(onClick = { isSettingsOpen = !isSettingsOpen }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = if (isSettingsOpen) accent else pageText
                    )
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = pageBg,
                titleContentColor = pageText,
                actionIconContentColor = pageText
            )
        )

        // 顶部进度条
        Box(Modifier.fillMaxWidth().height(3.dp).background(pageBg)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressAnim.coerceIn(0f, 1f))
                    .background(accent)
            )
        }

        if (isSettingsOpen) {
            SettingsPanel(
                fontSize = fontSize,
                lineHeight = lineHeight,
                eyeCare = eyeCare,
                accent = accent,
                onFontSizeChange = onFontSizeChange,
                onLineHeightChange = onLineHeightChange,
                onEyeCareToggle = onEyeCareToggle
            )
        }

        if (chapters.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accent)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { chapterIndex ->
                val chapter = chapters.getOrNull(chapterIndex)
                if (chapter != null) {
                    ChapterContent(
                        chapter = chapter,
                        readerEngine = readerEngine,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        eyeCare = eyeCare,
                        accent = accent,
                        onLoaded = {}
                    )
                }
            }
        }
    }

    // 目录抽屉（覆盖层，不影响主布局高度）
    if (showToc) {
        TocDrawer(
            book = book,
            chapters = chapters,
            currentChapter = currentChapterIndex,
            progressAnim = progressAnim,
            accent = accent,
            onChapterClick = { index ->
                pagerState.scrollToPage(index)
                showToc = false
            },
            onDismiss = { showToc = false }
        )
    }
}

// ---------- 设置面板 ----------

@Composable
private fun SettingsPanel(
    fontSize: Float,
    lineHeight: Float,
    eyeCare: Boolean,
    accent: Color,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onEyeCareToggle: (Boolean) -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)

    Column(
        Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 字号
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("字号", style = MaterialTheme.typography.labelLarge)
            Text(
                "${fontSize.roundToInt()}sp",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
        Slider(
            value = fontSize,
            onValueChange = onFontSizeChange,
            valueRange = 10f..28f,
            steps = 17,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.2f)
            )
        )

        // 行距
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("行距", style = MaterialTheme.typography.labelLarge)
            Text(
                "${(lineHeight * 10).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
        Slider(
            value = lineHeight,
            onValueChange = onLineHeightChange,
            valueRange = 1.0f..2.5f,
            steps = 14,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.2f)
            )
        )

        // 护眼
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LightMode,
                    contentDescription = null,
                    tint = if (eyeCare) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.width(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("护眼模式", style = MaterialTheme.typography.labelLarge)
            }
            Switch(
                checked = eyeCare,
                onCheckedChange = onEyeCareToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedColor = accent
                )
            )
        }

    }
}

// ---------- 目录抽屉 ----------

@Composable
private fun TocDrawer(
    book: BookResult,
    chapters: List<Chapter>,
    currentChapter: Int,
    progressAnim: Float,
    accent: Color,
    onChapterClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val drawerBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.TopEnd
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(drawerBg, RoundedCornerShape(16.dp, 0.dp, 0.dp, 16.dp))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "目录",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // 阅读进度
                if (chapters.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "阅读进度 ${((progressAnim.coerceIn(0f, 1f) * 100)).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent.copy(alpha = 0.15f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressAnim.coerceIn(0f, 1f))
                                .background(accent)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        book.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (chapters.isEmpty()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accent)
                    }
                } else {
                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(chapters, key = { it.index }) { (index, chapter) ->
                            val isSelected = index == currentChapter
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) accent.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable { onChapterClick(index) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) accent
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .width(28.dp)
                                        .align(Alignment.CenterVertically)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    chapter.title,
                                    style = if (isSelected) MaterialTheme.typography.bodyMedium
                                    else MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 正文页 ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterContent(
    chapter: Chapter,
    readerEngine: ReaderEngine,
    fontSize: Float,
    lineHeight: Float,
    eyeCare: Boolean,
    accent: Color,
    onLoaded: () -> Unit
) {
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chapter.url) {
        loading = true
        loadError = null
        content = null
        val result = runCatching { readerEngine.getContent(chapter.url) }
        result.onSuccess {
            content = it
            loading = false
            onLoaded()
        }
        result.onFailure { e ->
            loadError = e.message ?: "加载失败"
            loading = false
            onLoaded()
        }
    }

    val pageBg = if (eyeCare) Color(0xFFF5F0DC) else MaterialTheme.colorScheme.background
    val pageText = if (eyeCare) Color(0xFF5C5546) else MaterialTheme.colorScheme.onBackground

    if (loading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pageBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = accent)
        }
    } else if (loadError != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pageBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    loadError ?: "加载失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pageText.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "请检查网络连接或稍后重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = pageText.copy(alpha = 0.5f)
                )
            }
        }
    } else {
        val textContent = content ?: ""
        Column(
            Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 20.dp,
                    bottom = 24.dp
                )
        ) {
            // 章节标题
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = pageText,
                    fontSize = (fontSize * 1.4f).sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )

            // 正文
            if (textContent.isNotBlank()) {
                // 按段落拆分，提升排版可读性
                textContent
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .forEach { paragraph ->
                        Text(
                            text = paragraph.trim(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * lineHeight).sp,
                                color = pageText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = ((fontSize * 0.4f)).dp)
                        )
                    }
            } else {
                Text(
                    "暂无正文",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pageText.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    textAlign = TextAlign.Center
                )
            }

            // 章节尾部分隔线
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.4f)
                    .height(1.dp)
                    .background(accent.copy(alpha = 0.3f))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "— 第 ${chapter.index + 1} 章完 —",
                style = MaterialTheme.typography.labelSmall,
                color = pageText.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
