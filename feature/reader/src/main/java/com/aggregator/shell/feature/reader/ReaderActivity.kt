package com.aggregator.shell.feature.reader

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import coil.compose.AsyncImage
import com.aggregator.shell.core.data.local.BookshelfDao
import com.aggregator.shell.core.data.local.FavoritesDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.BookshelfEntity
import com.aggregator.shell.core.data.local.entity.FavoritesEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.data.di.appDataStore
import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.ReaderEngine
import com.aggregator.shell.core.ui.components.EmptyState
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reader feature shell: a keyword search box, book result list with cover
 * art, and empty / error / retry states. A user-imported Legado book source
 * (Room) is preferred by the engine; the built-in demo is the fallback.
 * Includes bookshelf management for saved books and a chapter reading view
 * with page-turning modes and basic layout (font size / line height / eye-care).
 */
@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class ReaderActivity : ComponentActivity() {

    @Inject
    lateinit var readerEngine: ReaderEngine

    @Inject
    lateinit var bookshelfDao: BookshelfDao

    @Inject
    lateinit var favoritesDao: FavoritesDao

    @Inject
    lateinit var searchHistoryDao: SearchHistoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { ReaderUi() } }
    }

    @Composable
    private fun ReaderUi() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var tabIndex by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("斗破苍穹") }
        var books by remember { mutableStateOf<List<BookResult>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var bookshelf by remember { mutableStateOf<List<BookshelfEntity>>(emptyList()) }
        var favorites by remember { mutableStateOf<List<FavoritesEntity>>(emptyList()) }
        var searchHistory by remember { mutableStateOf<List<SearchHistoryEntity>>(emptyList()) }

        // 阅读状态：点击书籍后进入阅读页
        var readingBook by remember { mutableStateOf<BookResult?>(null) }
        var bookResumeChapter by remember { mutableStateOf("") }

        // 排版设置
        var fontSize by remember { mutableFloatStateOf(16f) }
        var lineHeight by remember { mutableFloatStateOf(1.6f) }
        var eyeCare by remember { mutableStateOf(false) }

        fun saveReaderSettings() {
            scope.launch {
                context.appDataStore.edit { p ->
                    p[KEY_FONT_SIZE] = fontSize
                    p[KEY_LINE_HEIGHT] = lineHeight
                    p[KEY_EYE_CARE] = eyeCare
                }
            }
        }

        fun openReader(book: BookResult, resumeChapter: String = "") {
            readingBook = book
            bookResumeChapter = resumeChapter
            scope.launch {
                bookshelfDao.upsert(
                    BookshelfEntity(
                        bookId = book.id,
                        sourceId = book.sourceName,
                        name = book.name,
                        author = book.author,
                        coverUrl = book.coverUrl,
                        lastReadTime = System.currentTimeMillis(),
                        readProgress = 0f,
                        lastChapter = resumeChapter
                    )
                )
            }
        }

        fun saveReadingProgress(bookId: String, chapterTitle: String) {
            scope.launch {
                val existing = bookshelfDao.findById(bookId)
                if (existing != null) {
                    bookshelfDao.upsert(
                        existing.copy(
                            lastChapter = chapterTitle,
                            lastReadTime = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        fun runSearch() {
            val kw = query.ifBlank { "斗破苍穹" }
            loading = true
            error = null
            scope.launch {
                searchHistoryDao.upsert(
                    SearchHistoryEntity(
                        query = kw,
                        module = "reader",
                        lastUsed = System.currentTimeMillis()
                    )
                )
                val res = runCatching { readerEngine.search(kw, 1) }
                res.onSuccess { books = it }
                res.onFailure { error = it.message ?: "搜索失败" }
                loading = false
                searchHistoryDao.byModule("reader").first().let { searchHistory = it }
            }
        }

        fun loadSearchHistory() {
            scope.launch {
                searchHistoryDao.byModule("reader").first().let { searchHistory = it }
            }
        }

        fun removeSearchHistory(q: String) {
            scope.launch {
                searchHistoryDao.remove("reader", q)
                loadSearchHistory()
            }
        }

        fun clearSearchHistory() {
            scope.launch {
                searchHistoryDao.clearByModule("reader")
                loadSearchHistory()
            }
        }

        fun loadBookshelf() {
            scope.launch {
                bookshelfDao.all().first().let { bookshelf = it }
            }
        }

        fun loadFavorites() {
            scope.launch {
                favoritesDao.byModule("reader").first().let { favorites = it }
            }
        }

        fun addToBookshelf(book: BookResult) {
            scope.launch {
                val bookshelfItem = BookshelfEntity(
                    bookId = book.id,
                    sourceId = book.sourceName,
                    name = book.name,
                    author = book.author,
                    coverUrl = book.coverUrl,
                    lastReadTime = System.currentTimeMillis(),
                    readProgress = 0f
                )
                bookshelfDao.upsert(bookshelfItem)
            }
        }

        fun removeFromBookshelf(bookId: String) {
            scope.launch {
                bookshelfDao.remove(bookId)
            }
        }

        fun addToFavorites(book: BookResult) {
            scope.launch {
                val favorite = FavoritesEntity(
                    id = "fav_${book.id}_${System.currentTimeMillis()}",
                    sourceId = book.sourceName,
                    contentId = book.id,
                    title = book.name,
                    coverUrl = book.coverUrl,
                    module = "reader",
                    addedTime = System.currentTimeMillis(),
                    category = "books"
                )
                favoritesDao.upsert(favorite)
            }
        }

        fun removeFromFavorites(id: String) {
            scope.launch {
                favoritesDao.remove(id)
            }
        }

        fun isInFavorites(bookId: String): Boolean {
            return favorites.any { it.contentId == bookId }
        }

        LaunchedEffect(Unit) {
            val prefs = context.appDataStore.data.first()
            fontSize = prefs[KEY_FONT_SIZE] ?: 16f
            lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.6f
            eyeCare = prefs[KEY_EYE_CARE] ?: false
            loadBookshelf()
            loadFavorites()
            loadSearchHistory()
        }

        if (readingBook != null) {
            // 阅读页
            ReaderPageUi(
                book = readingBook!!,
                readerEngine = readerEngine,
                startIndex = 0,
                resumeChapter = bookResumeChapter,
                fontSize = fontSize,
                lineHeight = lineHeight,
                eyeCare = eyeCare,
                onBack = { chapterTitle ->
                    saveReadingProgress(readingBook!!.id, chapterTitle)
                    loadBookshelf()
                    readingBook = null
                    bookResumeChapter = ""
                },
                onFontSizeChange = { fontSize = it; saveReaderSettings() },
                onLineHeightChange = { lineHeight = it; saveReaderSettings() },
                onEyeCareToggle = { eyeCare = it; saveReaderSettings() }
            )
        } else {
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
                            if (tabIndex == 0) {
                                IconButton(onClick = { runSearch() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (tabIndex == 0) {
                        androidx.compose.material3.FloatingActionButton(onClick = { /* source editor (see Settings) */ }) {
                            Icon(Icons.Default.Search, contentDescription = "添加书源")
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    TabRow(selectedTabIndex = tabIndex) {
                        Tab(
                            selected = tabIndex == 0,
                            onClick = { tabIndex = 0 },
                            text = { Text("搜索") },
                            icon = { Icon(Icons.Default.Search, contentDescription = "搜索") }
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            text = { Text("书架") },
                            icon = { Icon(Icons.Default.Book, contentDescription = "书架") }
                        )
                        Tab(
                            selected = tabIndex == 2,
                            onClick = { tabIndex = 2 },
                            text = { Text("收藏") },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = "收藏") }
                        )
                    }

                    when (tabIndex) {
                        0 -> {
                            // Search Tab
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

                            // 搜索历史标签
                            if (searchHistory.isNotEmpty()) {
                                Row(
                                    Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    searchHistory.forEach { h ->
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = h.query,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.clickable {
                                                        query = h.query
                                                        runSearch()
                                                    }
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "×",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                    modifier = Modifier.clickable { removeSearchHistory(h.query) }
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "清空",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .clickable { clearSearchHistory() }
                                    )
                                }
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
                                    onAction = { runSearch() }
                                )

                                books.isEmpty() -> EmptyState(
                                    title = "暂无书籍",
                                    hint = "换个关键词或在设置中导入书源",
                                    actionLabel = "重新搜索",
                                    onAction = { runSearch() }
                                )

                                else -> LazyColumn(Modifier.fillMaxSize()) {
                                    items(books) { book ->
                                        BookCard(
                                            book = book,
                                            onAddToBookshelf = { addToBookshelf(book) },
                                            onAddToFavorites = { addToFavorites(book) },
                                            isInBookshelf = bookshelf.any { it.bookId == book.id },
                                            isInFavorites = isInFavorites(book.id),
                                            onRead = { openReader(book) }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Bookshelf Tab
                            when {
                                bookshelf.isEmpty() -> EmptyState(
                                    title = "书架空空",
                                    hint = "去搜索页添加喜欢的书籍",
                                    actionLabel = "去搜索",
                                    onAction = { tabIndex = 0 }
                                )

                                else -> LazyColumn(Modifier.fillMaxSize()) {
                                    items(bookshelf) { book ->
                                        BookshelfCard(
                                            book = book,
                                            onRemove = { removeFromBookshelf(book.bookId) },
                                            onRead = { openReader(
                                                BookResult(
                                                    id = book.bookId,
                                                    name = book.name,
                                                    author = book.author,
                                                    coverUrl = book.coverUrl,
                                                    bookUrl = book.bookId,
                                                    sourceName = book.sourceId
                                                ),
                                                resumeChapter = book.lastChapter
                                            ) }
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Favorites Tab
                            when {
                                favorites.isEmpty() -> EmptyState(
                                    title = "收藏空空",
                                    hint = "去搜索页添加喜欢的书籍",
                                    actionLabel = "去搜索",
                                    onAction = { tabIndex = 0 }
                                )

                                else -> LazyColumn(Modifier.fillMaxSize()) {
                                    items(favorites) { favorite ->
                                        FavoriteCard(
                                            favorite = favorite,
                                            onRemove = { removeFromFavorites(favorite.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BookCard(
        book: BookResult,
        onAddToBookshelf: () -> Unit,
        onAddToFavorites: () -> Unit,
        isInBookshelf: Boolean,
        isInFavorites: Boolean,
        onRead: () -> Unit
    ) {
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
                    model = book.coverUrl.ifEmpty { null },
                    contentDescription = book.name,
                    modifier = Modifier
                        .size(84.dp)
                        .aspectRatio(3f / 4f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        book.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (book.sourceName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "源: ${book.sourceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRead) { Text("开始阅读") }
                }
                Spacer(Modifier.width(8.dp))
                Row {
                    IconButton(
                        onClick = onAddToBookshelf,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = if (isInBookshelf) Icons.Default.Book else Icons.Outlined.Book,
                            contentDescription = if (isInBookshelf) "已在书架" else "添加到书架",
                            tint = if (isInBookshelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onAddToFavorites,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = if (isInFavorites) Icons.Default.Bookmark else Icons.Outlined.Bookmark,
                            contentDescription = if (isInFavorites) "已收藏" else "添加收藏",
                            tint = if (isInFavorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun BookshelfCard(book: BookshelfEntity, onRemove: () -> Unit, onRead: () -> Unit) {
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
                    model = book.coverUrl.ifEmpty { null },
                    contentDescription = book.name,
                    modifier = Modifier
                        .size(84.dp)
                        .aspectRatio(3f / 4f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        book.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "上次阅读: ${formatDate(book.lastReadTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (book.readProgress > 0f) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { book.readProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "进度: ${String.format("%.1f%%", book.readProgress * 100)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRead) { Text("继续阅读") }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = "从书架移除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    @Composable
    private fun FavoriteCard(favorite: FavoritesEntity, onRemove: () -> Unit) {
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
                    model = favorite.coverUrl?.ifEmpty { null },
                    contentDescription = favorite.title,
                    modifier = Modifier
                        .size(84.dp)
                        .aspectRatio(3f / 4f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        favorite.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "收藏时间: ${formatDate(favorite.addedTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (favorite.category.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "分类: ${favorite.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = "取消收藏",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return format.format(date)
    }

    companion object {
        private val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        private val KEY_EYE_CARE = booleanPreferencesKey("reader_eye_care")
    }
}
