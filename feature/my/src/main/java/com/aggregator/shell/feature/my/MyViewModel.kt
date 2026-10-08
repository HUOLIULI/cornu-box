package com.aggregator.shell.feature.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregator.shell.core.data.local.BookshelfDao
import com.aggregator.shell.core.data.local.FavoriteDao
import com.aggregator.shell.core.data.local.PlayHistoryDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.BookshelfEntity
import com.aggregator.shell.core.data.local.entity.FavoriteEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "我的"聚合页状态：书架 / 追剧夹（影视+音乐收藏）/ 播放历史 / 搜索历史。 */
data class MyUiState(
    val bookshelf: List<BookshelfEntity> = emptyList(),
    val videoFavorites: List<FavoriteEntity> = emptyList(),
    val musicFavorites: List<FavoriteEntity> = emptyList(),
    val playHistory: List<PlayHistoryEntity> = emptyList(),
    val searchHistory: List<SearchHistoryEntity> = emptyList()
)

@HiltViewModel
class MyViewModel @Inject constructor(
    private val bookshelfDao: BookshelfDao,
    private val favoriteDao: FavoriteDao,
    private val playHistoryDao: PlayHistoryDao,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    private val _ui = MutableStateFlow(MyUiState())
    val ui: StateFlow<MyUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { bookshelfDao.all().collectLatest { s -> _ui.value = _ui.value.copy(bookshelf = s) } }
        viewModelScope.launch { favoriteDao.byModule("VIDEO").collectLatest { s -> _ui.value = _ui.value.copy(videoFavorites = s) } }
        viewModelScope.launch { favoriteDao.byModule("MUSIC").collectLatest { s -> _ui.value = _ui.value.copy(musicFavorites = s) } }
        viewModelScope.launch { playHistoryDao.byModule("VIDEO").collectLatest { s -> _ui.value = _ui.value.copy(playHistory = s) } }
        viewModelScope.launch { searchHistoryDao.recent().collectLatest { s -> _ui.value = _ui.value.copy(searchHistory = s) } }
    }

    fun removeBookshelf(bookId: String) = viewModelScope.launch { bookshelfDao.remove(bookId) }

    fun removeVideoFavorite(id: String) = viewModelScope.launch { favoriteDao.remove(id) }

    fun removeMusicFavorite(id: String) = viewModelScope.launch { favoriteDao.remove(id) }

    fun clearSearchHistory() = viewModelScope.launch { searchHistoryDao.clearAll() }
}
