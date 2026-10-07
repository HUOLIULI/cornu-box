package com.aggregator.shell.feature.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MusicActivity : ComponentActivity() {

    @Inject
    lateinit var musicEngine: MusicEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { MusicShellUi() } }
    }

    @Composable
    private fun MusicShellUi() {
        var songs by remember { mutableStateOf<List<MusicResult>>(emptyList()) }
        LaunchedEffect(Unit) {
            songs = runCatching { musicEngine.search("演示") }.getOrDefault(emptyList())
        }
        Scaffold(
            topBar = { TopAppBar(title = { Text("音乐") }) }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(songs) { s ->
                    ListItem(
                        headlineContent = { Text(s.title) },
                        supportingContent = { Text("${s.artist} - ${s.album}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
