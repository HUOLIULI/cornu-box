package com.aggregator.shell.feature.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class ReaderActivity : ComponentActivity() {

    @Inject
    lateinit var readerEngine: ReaderEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { ReaderUi() } }
    }

    @Composable
    private fun ReaderUi() {
        var books by remember { mutableStateOf<List<BookResult>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            loading = true
            books = runCatching { readerEngine.search("斗破苍穹") }.getOrDefault(emptyList())
            loading = false
        }
        Scaffold(
            topBar = { TopAppBar(title = { Text("阅读") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { /* source editor */ }) { Text("+") }
            }
        ) { padding ->
            if (loading) {
                Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(books) { b ->
                        Card(Modifier.fillMaxWidth().padding(6.dp)) {
                            Row(Modifier.padding(12.dp)) {
                                Text(b.name, modifier = Modifier.weight(1f))
                                Text(b.author, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}
