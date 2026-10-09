package com.aggregator.shell.feature.music

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregator.shell.core.data.local.FavoritesDao
import com.aggregator.shell.core.data.local.PlayHistoryDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.FavoritesEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 音乐播放 UI 状态：搜索 + 播放队列 + 通知栏控制 + 历史/收藏。
 * 播放通过 [MusicNotificationService] 承载（ExoPlayerCore），
 * 通知栏提供播放/暂停/上一曲/下一曲。
 */
data class MusicPlayUiState(
    val current: MusicResult? = null,
    val playing: Boolean = false,
    val error: String? = null,
    val queue: List<MusicResult> = emptyList(),
    val queueIndex: Int = -1
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicEngine: MusicEngine,
    private val playHistoryDao: PlayHistoryDao,
    private val favoritesDao: FavoritesDao,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    private val _results = MutableStateFlow(emptyList<MusicResult>())
    val results: StateFlow<List<MusicResult>> = _results.asStateFlow()

    private val _loadingResults = MutableStateFlow(false)
    val loadingResults: StateFlow<Boolean> = _loadingResults.asStateFlow()

    private val _play = MutableStateFlow(MusicPlayUiState())
    val play: StateFlow<MusicPlayUiState> = _play.asStateFlow()

    private val _playHistory = MutableStateFlow(emptyList<PlayHistoryEntity>())
    val playHistory: StateFlow<List<PlayHistoryEntity>> = _playHistory.asStateFlow()

    private val _favorites = MutableStateFlow(emptyList<FavoritesEntity>())
    val favorites: StateFlow<List<FavoritesEntity>> = _favorites.asStateFlow()

    private val _searchHistory = MutableStateFlow(emptyList<SearchHistoryEntity>())
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = _searchHistory.asStateFlow()

    private var progressSaveJob: Job? = null

    fun refresh(keyword: String = "演示") {
        viewModelScope.launch {
            if (keyword.isNotBlank()) {
                searchHistoryDao.upsert(
                    SearchHistoryEntity(
                        query = keyword,
                        module = "music",
                        lastUsed = System.currentTimeMillis()
                    )
                )
            }
            _loadingResults.value = true
            val items = runCatching { musicEngine.search(keyword) }.getOrDefault(emptyList())
            _results.value = items
            _loadingResults.value = false
        }
    }

    fun getSearchHistory() {
        viewModelScope.launch {
            searchHistoryDao.byModule("music").collect { _searchHistory.value = it }
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            searchHistoryDao.remove("music", query)
            getSearchHistory()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearByModule("music")
            _searchHistory.value = emptyList()
        }
    }

    /** 播放指定歌曲，加入队列并同步 Service 静态状态 */
    fun play(song: MusicResult, context: Context? = null) {
        val queue = _results.value
        val idx = queue.indexOf(song)
        if (idx >= 0) {
            MusicNotificationService.currentQueue = queue.toMutableList()
            MusicNotificationService.currentIndex = idx
        }
        _play.value = _play.value.copy(
            current = song,
            playing = true,
            queue = queue,
            queueIndex = idx,
            error = null
        )
        startProgressSave()
        // 查历史进度，设置 pendingSeek（同步，避免协程竞态）
        viewModelScope.launch {
            val h = runCatching {
                playHistoryDao.byId("hist_${song.id.hashCode()}")
            }.getOrNull()
            if (h != null && h.positionMs > 1000L) {
                MusicNotificationService.pendingSeekMs = h.positionMs
            }
            // 等待 pendingSeek 设置完成后再通知 Service，防止竞态
            context?.let { startServicePlayback(it) }
        }
    }

    /** 由 Activity 调用：通过 startService 触发播放 */
    fun startServicePlayback(context: Context) {
        val intent = Intent(context, MusicNotificationService::class.java)
            .putExtra("ACTION", "play_now")
        context.startService(intent)
    }

    fun pause(context: Context) {
        _play.value = _play.value.copy(playing = false)
        sendServiceCommand(context, "pause")
    }

    fun resume(context: Context) {
        _play.value = _play.value.copy(playing = true)
        sendServiceCommand(context, "play")
    }

    /** 通知栏下一曲 */
    fun nextTrack(context: Context) {
        val queue = _play.value.queue
        val idx = (_play.value.queueIndex + 1).coerceAtMost(queue.size - 1)
        if (idx >= 0) {
            MusicNotificationService.currentIndex = idx
            play(queue[idx], context)
        }
    }

    /** 通知栏上一曲 */
    fun prevTrack(context: Context) {
        val queue = _play.value.queue
        val idx = (_play.value.queueIndex - 1).coerceAtLeast(0)
        if (idx <= _play.value.queueIndex) {
            MusicNotificationService.currentIndex = idx
            play(queue[idx], context)
        }
    }

    private fun sendServiceCommand(context: Context, action: String) {
        val intent = Intent(context, MusicNotificationService::class.java)
            .putExtra("ACTION", action)
        context.startService(intent)
    }

    fun stop() {
        progressSaveJob?.cancel()
        progressSaveJob = null
        _play.value = MusicPlayUiState()
    }

    /** 获取播放历史 */
    fun getPlayHistory() {
        viewModelScope.launch {
            playHistoryDao.byModule("music").collect { _playHistory.value = it }
        }
    }

    /** 获取收藏 */
    fun getFavorites() {
        viewModelScope.launch {
            favoritesDao.byModule("music").collect { _favorites.value = it }
        }
    }

    fun addToFavorites(song: MusicResult) {
        viewModelScope.launch {
            val fav = FavoritesEntity(
                id = "fav_${UUID.randomUUID()}",
                sourceId = song.source,
                contentId = song.id,
                title = "${song.title} - ${song.artist}",
                coverUrl = song.picUrl,
                module = "music",
                addedTime = System.currentTimeMillis(),
                category = "songs"
            )
            favoritesDao.upsert(fav)
            getFavorites()
        }
    }

    fun removeFromFavorites(id: String) {
        viewModelScope.launch {
            favoritesDao.remove(id)
        }
    }

    fun clearFavorites() {
        viewModelScope.launch {
            favoritesDao.clearByModule("music")
        }
    }

    private fun upsertHistory(song: MusicResult, positionMs: Long) {
        viewModelScope.launch {
            val h = PlayHistoryEntity(
                id = "hist_${song.id.hashCode()}",
                sourceId = song.source,
                contentId = song.id,
                title = "${song.title} - ${song.artist}",
                positionMs = positionMs,
                module = "music",
                updated = System.currentTimeMillis()
            )
            playHistoryDao.upsert(h)
        }
    }

    private fun startProgressSave() {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                val song = _play.value.current ?: break
                if (_play.value.playing) {
                    val pos = MusicNotificationService.getCurrentPositionMs()
                    upsertHistory(song, pos)
                }
            }
        }
    }

    /** 播放前若存在历史进度，则自动 seek 到上次位置 */
    init {
        getPlayHistory()
        getFavorites()
        getSearchHistory()
        refresh()
    }
}
