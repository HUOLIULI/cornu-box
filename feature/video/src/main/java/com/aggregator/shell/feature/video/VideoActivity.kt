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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
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
    private fun VideoShellUi() {
        var tabIndex by remember { mutableIntStateOf(0) }
        var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var refreshKey by remember { mutableIntStateOf(0) }

        LaunchedEffect(refreshKey) {
            loading = true
            results = runCatching { videoEngine.search("演示", 1) }.getOrDefault(emptyList())
            loading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("影视") },
                    actions = {
                        IconButton(onClick = { refreshKey++ }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("点播") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("短剧") })
                    Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("IPTV") })
                }
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Row(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator() }
                    tabIndex == 0 -> LazyColumn(Modifier.fillMaxSize()) {
                        items(results) { v ->
                            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("源: ${v.sourceKey}  ·  ${v.type}")
                                }
                            }
                        }
                        if (results.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("暂无结果，点击右上角刷新（内置演示源）")
                                }
                            }
                        }
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("「${if (tabIndex == 1) "短剧" else "IPTV"}」模块已预留接口，v1.1 落地")
                    }
                }
            }
        }
    }
}
