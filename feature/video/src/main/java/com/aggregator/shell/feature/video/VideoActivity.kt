package com.aggregator.shell.feature.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoActivity : ComponentActivity() {

    @Inject
    lateinit var videoEngine: VideoEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                VideoShellUi()
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun VideoShellUi() {
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }

        LaunchedEffect(tab) {
            loading = true
            val kw = when (tab) {
                1 -> "短剧"
                2 -> "IPTV"
                else -> "演示"
            }
            results = runCatching { videoEngine.search(kw, 1) }.getOrDefault(emptyList())
            loading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("影视 · " + when (tab) { 1 -> "短剧"; 2 -> "IPTV"; else -> "点播" }) },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                results = runCatching {
                                    videoEngine.search(when (tab) { 1 -> "短剧"; 2 -> "IPTV"; else -> "演示" }, 1)
                                }.getOrDefault(emptyList())
                            }
                        }) { Icon(Icons.Default.PlayArrow, contentDescription = "刷新") }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        content = { Text("点播") }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        content = { Text("短剧") }
                    )
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        content = { Text("IPTV") }
                    )
                }
                when (tab) {
                    1 -> ShortDramaSwipe(results, loading)
                    else -> VodeoResultList(results, loading)
                }
            }
        }
    }

    @Composable
    private fun ShortDramaSwipe(results: List<VideoResult>, loading: Boolean) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        // Horizontal swipe between short-drama items; each card keeps 9:16 portrait aspect.
        LazyRow(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            items(results) { v ->
                Card(Modifier.height(360.dp).fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("源: ${v.sourceKey}  ·  ${v.type}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text("上下滑切换下一集（占位）", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    @Composable
    private fun VodeoResultList(results: List<VideoResult>, loading: Boolean) {
        if (loading) {
            Row(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results) { v ->
                    Card(Modifier.fillMaxWidth().padding(8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("源: ${v.sourceKey}  ·  ${v.type}")
                        }
                    }
                }
            }
        }
    }
}
