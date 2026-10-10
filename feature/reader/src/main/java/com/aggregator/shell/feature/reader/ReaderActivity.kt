package com.aggregator.shell.feature.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.aggregator.shell.core.media.player.PlayerCore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import coil.compose.AsyncImage
import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.Chapter
import com.aggregator.shell.core.ui.components.CardPeek
import com.aggregator.shell.core.ui.components.EmptyState
import com.aggregator.shell.core.ui.theme.AppTheme

/**
 * Reader feature：书架 / 搜索（含最近搜索 chips）/ 目录 / 正文 四段式。
 *
 * 顶部 Tab 切「书架」「搜索」；书架驱动"继续读"；搜索点书进入目录，
 * 选章读正文并自动回写阅读进度到书架（Room）。数据走 [ReaderViewModel]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class ReaderActivity : ComponentActivity() {

    private val vm: ReaderViewModel by viewModels()

    @Inject
    lateinit var playerCore: PlayerCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerCore.initialize(this)
        val extraBookId = intent.getStringExtra("extra_book_id")
        val extraBookSource = intent.getStringExtra("extra_book_source")
        val extraBookName = intent.getStringExtra("extra_book_name")
        val extraBookAuthor = intent.getStringExtra("extra_book_author")
        val extraBookCover = intent.getStringExtra("extra_book_cover")
        val extraBookUrl = intent.getStringExtra("extra_book_url")
        val extraBook = if (extraBookId != null) {
            BookResult(
                id = extraBookId,
                name = extraBookName.orEmpty(),
                author = extraBookAuthor.orEmpty(),
                coverUrl = extraBookCover.orEmpty(),
                bookUrl = extraBookUrl.orEmpty(),
                sourceName = extraBookSource.orEmpty()
            )
        } else null
        setContent {
            AppTheme {
                var tabIndex by remember { mutableIntStateOf(0) }
                var query by remember { mutableStateOf("斗破苍穹") }
                val ui = vm.ui.collectAsState()
                if (extraBook != null) {
                    LaunchedEffect(Unit) { vm.openBook(extraBook) }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("阅读") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (ui.value.currentBook != null) vm.backToShelf() else finish()
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.search(query) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        when {
                            ui.value.currentBook != null -> BookDetailScreen(
                                ui = ui.value,
                                onBack = { vm.backToShelf() },
                                onOpenChapter = { vm.openChapter(it) },
                                onRemoveFromShelf = { vm.removeFromBookshelf(it) },
                                onSpeak = { vm.speak() },
                                onStopSpeak = { vm.stopSpeaking() }
                            )

                            else -> {
                                TabRow(selectedTabIndex = tabIndex) {
                                    Tab(
                                        selected = tabIndex == 0,
                                        onClick = { tabIndex = 0 },
                                        text = { Text("书架") }
                                    )
                                    Tab(
                                        selected = tabIndex == 1,
                                        onClick = { tabIndex = 1 },
                                        text = { Text("搜索") }
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                when (tabIndex) {
                                    0 -> BookshelfScreen(ui = ui.value) { vm.openBook(it) }
                                    1 -> SearchScreen(
                                        ui = ui.value,
                                        query = query,
                                        onQueryChange = { query = it },
                                        onSearch = { vm.search(it) },
                                        onOpenBook = { vm.openBook(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** 从「我的」书架区拉起本书阅读（携带 [BookResult] 进入目录/正文链路）。 */
        fun launchBook(ctx: android.content.Context, book: com.aggregator.shell.core.source.api.BookResult) {
            ctx.startActivity(
                android.content.Intent(ctx, ReaderActivity::class.java)
                    .putExtra("extra_book_id", book.id)
                    .putExtra("extra_book_source", book.sourceName)
                    .putExtra("extra_book_name", book.name)
                    .putExtra("extra_book_author", book.author)
                    .putExtra("extra_book_cover", book.coverUrl)
                    .putExtra("extra_book_url", book.bookUrl)
            )
        }
    }
}

/** 书架：「继续读」列表，显示进度，可移除。 */
@Composable
private fun BookshelfScreen(
    ui: ReaderUiState,
    onOpen: (BookResult) -> Unit
) {
    if (ui.bookshelf.isEmpty()) {
        EmptyState(
            title = "书架为空",
            hint = "搜索并打开一本书即可加入书架",
            actionLabel = "去搜索",
            onAction = { }
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(ui.bookshelf) { shelf ->
            CardPeek(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable {
                        onOpen(
                            BookResult(
                                id = shelf.bookId,
                                name = shelf.name,
                                author = shelf.author,
                                coverUrl = shelf.coverUrl,
                                bookUrl = shelf.bookId,
                                sourceName = shelf.sourceId
                            )
                        )
                    }
            ) {
                Row(Modifier.padding(12.dp)) {
                    AsyncImage(
                        model = shelf.coverUrl.ifEmpty { null },
                        contentDescription = shelf.name,
                        modifier = Modifier
                            .size(84.dp)
                            .aspectRatio(3f / 4f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shelf.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(shelf.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        if (shelf.lastChapter.isNotBlank()) {
                            Text("上次读到：${shelf.lastChapter}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    if (shelf.readProgress > 0f) {
                        Text(
                            "${(shelf.readProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/** 搜索：最近搜索 chips + 结果列表，点书进入目录。 */
@Composable
private fun SearchScreen(
    ui: ReaderUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenBook: (BookResult) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索书名 / 作者") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                trailingIcon = {
                    IconButton(onClick = { onSearch(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )
        }
        val history = ui.searchHistory.filter { it.module == "READER" && it.keyword.isNotBlank() }.take(8)
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().height(44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { h ->
                    AssistChip(onClick = { onSearch(h.keyword) }, label = { Text(h.keyword) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        when {
            ui.searchLoading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> EmptyState(
                title = "加载失败",
                hint = ui.error ?: "搜索出错了",
                actionLabel = "重试",
                onAction = { onSearch(query) }
            )
            ui.books.isEmpty() -> EmptyState(
                title = "暂无书籍",
                hint = "换个关键词或在设置中导入书源",
                actionLabel = "重新搜索",
                onAction = { onSearch(query) }
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(ui.books) { b ->
                    BookCard(b) { onOpenBook(b) }
                }
            }
        }
    }
}

/** 书籍详情：目录列表 + 正文阅读（选章后展示）。 */
@Composable
private fun BookDetailScreen(
    ui: ReaderUiState,
    onBack: () -> Unit,
    onOpenChapter: (Chapter) -> Unit,
    onRemoveFromShelf: (String) -> Unit,
    onSpeak: () -> Unit = {},
    onStopSpeak: () -> Unit = {}
) {
    val book = ui.currentBook ?: return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            AsyncImage(
                model = book.coverUrl.ifEmpty { null },
                contentDescription = book.name,
                modifier = Modifier
                    .size(72.dp)
                    .aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onRemoveFromShelf(book.id) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("从书架移除")
                }
            }
        }

        if (ui.content.isBlank() && ui.currentChapter == null) {
            // 目录模式
            if (ui.tocLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.toc) { ch ->
                        Text(
                            text = "${ch.index}. ${ch.title}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChapter(ch) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        } else {
            // 正文模式
            ui.currentChapter?.let { ch ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第 ${ch.index} 章 · ${ch.title}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    OutlinedButton(onClick = { if (ui.ttsReading) onStopSpeak() else onSpeak() }) {
                        Text(if (ui.ttsReading) "停止朗读" else "TTS 朗读")
                    }
                    OutlinedButton(onClick = { onBack() }) { Text("返回目录") }
                }
                if (ui.contentLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = ui.content,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCard(b: BookResult, onClick: (BookResult) -> Unit) {
    CardPeek(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onClick(b) }
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth()) {
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
                Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(b.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                if (b.sourceName.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("源: ${b.sourceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }
    }
}
