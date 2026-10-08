package com.aggregator.shell.feature.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.ui.components.EmptyState
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Video feature shell: a search box on top, three tabs (点播 / 短剧 / IPTV),
 * and per-tab result lists. Search is user-driven — typing + confirm triggers
 * [VideoEngine.search]; each result card shows a cover image, title, and
 * source/type metadata.
 */
@AndroidEntryPoint
class VideoActivity : ComponentActivity() {

    @Inject
    lateinit var videoEngine: VideoEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme { VideoShellUi() }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun VideoShellUi() {
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        fun tabKeyword(t: Int): String = when (t) {
            1 -> "短剧"
            2 -> "IPTV"
            else -> "演示"
        }

        fun runSearch(t: Int) {
            loading = true
            error = null
            scope.launch {
                val kw = query.ifBlank { tabKeyword(t) }
                val res = runCatching { videoEngine.search(kw, 1) }
                res.onSuccess { results = it }
                res.onFailure {
                    error = it.message ?: "搜索失败"
                    results = emptyList()
                }
                loading = false
            }
        }

        LaunchedEffect(tab) { runSearch(tab) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (tab) {
                                0 -> "影视 · 点播"
                                1 -> "影视 · 短剧"
                                else -> "影视 · IPTV"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { runSearch(tab) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("搜索影视内容（回车触发）") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    trailingIcon = {
                        IconButton(onClick = { runSearch(tab) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("点播") }
                    Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("短剧") }
                    Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("IPTV") }
                }
                when {
                    loading -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    error != null -> EmptyState(
                        title = "加载失败",
                        hint = error ?: "搜索出错了",
                        actionLabel = "重试",
                        onAction = { runSearch(tab) }
                    )

                    results.isEmpty() -> EmptyState(
                        title = "暂无结果",
                        hint = "换个关键词或添加影视源",
                        actionLabel = "重新搜索",
                        onAction = { runSearch(tab) }
                    )

                    tab == 1 -> ShortDramaSwipe(results)
                    else -> VideoResultList(results)
                }
            }
        }
    }

    @Composable
    private fun ShortDramaSwipe(results: List<VideoResult>) {
        // Horizontal swipe between short-drama items; each card keeps 9:16 portrait.
        LazyRow(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { v ->
                Card(
                    Modifier.height(360.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = v.coverUrl.ifEmpty { null },
                            contentDescription = v.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            v.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "源: ${v.sourceKey}  ·  ${v.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "上下滑切换下一集（占位）",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun VideoResultList(results: List<VideoResult>) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(results) { v ->
                VideoCard(v)
            }
        }
    }

    @Composable
    private fun VideoCard(v: VideoResult) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = v.coverUrl.ifEmpty { null },
                    contentDescription = v.title,
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        v.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "源: ${v.sourceKey}  ·  ${v.type}  ·  ${v.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
