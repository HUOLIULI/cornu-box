package com.aggregator.shell.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregator.shell.core.data.local.BookshelfDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.BookshelfEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.source.api.BookResult
import com.aggregator.shell.core.source.api.Chapter
import com.aggregator.shell.core.source.api.ReaderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 阅读模块状态：搜索 + 书架 + 最近搜索 + 目录 + 正文 + 阅读进度。
 *
 * 链路：搜索（[ReaderEngine.search]）→ 点击书籍 → 目录（[ReaderEngine.getToc]）
 * → 选择章节 → 正文（[ReaderEngine.getContent]）→ 记录进度（[BookshelfDao] 回写 lastChapter
 * / readProgress），书架（[BookshelfDao.all]）驱动"继续读"。搜索历史落 [SearchHistoryDao]。
 */
data class ReaderUiState(
    val books: List<BookResult> = emptyList(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val bookshelf: List<BookshelfEntity> = emptyList(),
    val currentBook: BookResult? = null,
    val toc: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val content: String = "",
    val contentLoading: Boolean = false,
    val tocLoading: Boolean = false,
    val searchLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val readerEngine: ReaderEngine,
    private val bookshelfDao: BookshelfDao,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { bookshelfDao.all().collectLatest { s -> _ui.value = _ui.value.copy(bookshelf = s) } }
        viewModelScope.launch { searchHistoryDao.recent().collectLatest { s -> _ui.value = _ui.value.copy(searchHistory = s) } }
    }

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(searchLoading = true, error = null)
            runCatching {
                searchHistoryDao.upsert(
                    SearchHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        module = "READER",
                        keyword = keyword,
                        ts = System.currentTimeMillis()
                    )
                )
            }
            val res = runCatching { readerEngine.search(keyword, 1) }
            res.onSuccess { _ui.value = _ui.value.copy(books = it, searchLoading = false) }
            res.onFailure { _ui.value = _ui.value.copy(searchLoading = false, error = it.message ?: "搜索失败") }
        }
    }

    fun openBook(book: BookResult) {
        _ui.value = _ui.value.copy(currentBook = book, toc = emptyList(), currentChapter = null, content = "")
        viewModelScope.launch {
            _ui.value = _ui.value.copy(tocLoading = true)
            val toc = runCatching { readerEngine.getToc(book.bookUrl) }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(toc = toc, tocLoading = false)
        }
        // 加入 / 刷新书架
        addToBookshelf(book)
    }

    fun openChapter(chapter: Chapter) {
        _ui.value = _ui.value.copy(currentChapter = chapter, contentLoading = true, content = "")
        viewModelScope.launch {
            val content = runCatching { readerEngine.getContent(chapter.url.ifBlank { chapter.title }) }.getOrDefault("")
            _ui.value = _ui.value.copy(content = content, contentLoading = false)
            // 回写阅读进度
            val book = _ui.value.currentBook
            if (book != null && _ui.value.toc.isNotEmpty()) {
                val progress = (_ui.value.toc.indexOfFirst { it == chapter } + 1).toFloat() / _ui.value.toc.size
                updateBookshelf(book, chapter, progress)
            }
        }
    }

    fun backToShelf() {
        _ui.value = _ui.value.copy(currentBook = null, toc = emptyList(), currentChapter = null, content = "")
    }

    fun removeFromBookshelf(bookId: String) {
        viewModelScope.launch { bookshelfDao.remove(bookId) }
    }

    private fun addToBookshelf(book: BookResult) {
        viewModelScope.launch {
            bookshelfDao.upsert(
                BookshelfEntity(
                    bookId = book.id,
                    sourceId = book.sourceName,
                    name = book.name,
                    author = book.author,
                    coverUrl = book.coverUrl,
                    lastChapter = "",
                    lastReadTime = System.currentTimeMillis(),
                    readProgress = 0f
                )
            )
        }
    }

    private suspend fun updateBookshelf(book: BookResult, chapter: Chapter, progress: Float) {
        bookshelfDao.upsert(
            BookshelfEntity(
                bookId = book.id,
                sourceId = book.sourceName,
                name = book.name,
                author = book.author,
                coverUrl = book.coverUrl,
                lastChapter = chapter.title,
                lastReadTime = System.currentTimeMillis(),
                readProgress = progress.coerceIn(0f, 1f)
            )
        )
    }
}
