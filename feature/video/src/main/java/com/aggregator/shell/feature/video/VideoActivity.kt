package com.aggregator.shell.feature.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

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
        val scope = rememberCoroutineScope()
        var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            loading = true
            results = runCatching { videoEngine.search("演示", 1) }.getOrDefault(emptyList())
            loading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("影视") }, actions = {
                    IconButton(onClick = { /* refresh */ }) {
                        // no icon needed; keep the slot
                    }
                })
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = 0) {
                    Tab(selected = true, onClick = {}, content = { Text("点播") })
                    Tab(selected = false, onClick = {}, content = { Text("短剧") })
                    Tab(selected = false, onClick = {}, content = { Text("IPTV") })
                }
                Spacer(Modifier.height(8.dp))
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
    }
}
