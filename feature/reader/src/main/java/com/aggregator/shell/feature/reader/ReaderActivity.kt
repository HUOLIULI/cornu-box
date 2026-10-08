package com.aggregator.shell.feature.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.ui.components.EmptyState
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Reader feature shell: a keyword search box, book result list with cover
 * art, and empty / error / retry states. A user-imported Legado book source
 * (Room) is preferred by the engine; the built-in demo is the fallback.
 */
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
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("斗破苍穹") }
        var books by remember { mutableStateOf<List<BookResult>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        fun runSearch() {
            loading = true
            error = null
            scope.launch {
                val res = runCatching { readerEngine.search(query.ifBlank { "斗破苍穹" }, 1) }
                res.onSuccess { books = it }
                res.onFailure { error = it.message ?: "搜索失败" }
                loading = false
            }
        }

        LaunchedEffect(Unit) { runSearch() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("阅读") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { runSearch() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                )
            },
            floatingActionButton = {
                androidx.compose.material3.FloatingActionButton(onClick = { /* source editor (see Settings) */ }) {
                    Icon(Icons.Default.Search, contentDescription = "添加书源")
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("搜索书名 / 作者") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    trailingIcon = {
                        IconButton(onClick = { runSearch() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "搜索")
                        }
                    }
                )
                when {
                    loading -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    error != null -> EmptyState(
                        title = "加载失败",
                        hint = error ?: "搜索出错了",
                        actionLabel = "重试",
                        onAction = { runSearch() }
                    )

                    books.isEmpty() -> EmptyState(
                        title = "暂无书籍",
                        hint = "换个关键词或在设置中导入书源",
                        actionLabel = "重新搜索",
                        onAction = { runSearch() }
                    )

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(books) { BookCard(it) }
                    }
                }
            }
        }
    }

    @Composable
    private fun BookCard(b: BookResult) {
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
                    model = b.coverUrl.ifEmpty { null },
                    contentDescription = b.name,
                    modifier = Modifier
                        .size(84.dp)
                        .aspectRatio(3f / 4f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        b.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        b.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (b.sourceName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "源: ${b.sourceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
