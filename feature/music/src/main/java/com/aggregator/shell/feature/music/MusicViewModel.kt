package com.aggregator.shell.feature.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregator.shell.core.data.local.FavoriteDao
import com.aggregator.shell.core.data.local.PlayHistoryDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.FavoriteEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.media.lyric.LrcParser
import com.aggregator.shell.core.media.lyric.ParsedLyric
import com.aggregator.shell.core.source.api.MusicEngine
import com.aggregator.shell.core.source.api.MusicResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import android.app.Application

/** 播放页状态。 */
data class MusicPlayerUi(
    val current: PlayMediaItem? = null,
    val lyric: ParsedLyric = ParsedLyric(emptyList(), 0L),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<MusicResult> = emptyList(),
    val queueIndex: Int = -1
)

/**
 * 音乐模块状态：搜索 + 最近搜索 + 播放队列 + 歌词同步 + 断点。
 * 数据走 [MusicEngine]（LX 脚本 / 演示），播放走 [PlayerCore]，
 * 歌词经 [LrcParser] 解析后随 [PlayerCore.positionMs] 逐行高亮。
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicEngine: MusicEngine,
    private val playerCore: PlayerCore,
    private val searchHistoryDao: SearchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val playHistoryDao: PlayHistoryDao,
    private val app: Application
) : ViewModel() {

    private val _songs = MutableStateFlow(emptyList<MusicResult>())
    val songs: StateFlow<List<MusicResult>> = _songs.asStateFlow()

    private val _searchHistory = MutableStateFlow(emptyList<SearchHistoryEntity>())
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = _searchHistory.asStateFlow()

    private val _favorites = MutableStateFlow(emptyList<FavoriteEntity>())
    val favorites: StateFlow<List<FavoriteEntity>> = _favorites.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _player = MutableStateFlow(MusicPlayerUi())
    val player: StateFlow<MusicPlayerUi> = _player.asStateFlow()

    init {
        viewModelScope.launch { searchHistoryDao.recent().collectLatest { _searchHistory.value = it } }
        viewModelScope.launch { favoriteDao.byModule("MUSIC").collectLatest { _favorites.value = it } }
        // 镜像播放位置到歌词同步
        viewModelScope.launch {
            playerCore.positionMs.collect { pos ->
                _player.value = _player.value.copy(positionMs = pos)
            }
        }
        viewModelScope.launch {
            playerCore.state.collect { st ->
                _player.value = _player.value.copy(
                    isPlaying = st == com.aggregator.shell.core.media.player.PlayerState.Ready
                )
            }
        }
    }

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                searchHistoryDao.upsert(
                    SearchHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        module = "MUSIC",
                        keyword = keyword,
                        ts = System.currentTimeMillis()
                    )
                )
            }
            val res = runCatching { musicEngine.search(keyword) }
            res.onSuccess { _songs.value = it }
            res.onFailure { _error.value = it.message ?: "搜索失败" }
            _loading.value = false
        }
    }

    fun playSong(song: MusicResult, queue: List<MusicResult>) {
        var idx = queue.indexOfFirst { it.id == song.id }
        if (idx < 0) idx = 0
        _player.value = _player.value.copy(queue = queue, queueIndex = idx)
        playCurrent(queue, idx)
    }

    fun playNext() {
        val p = _player.value
        val q = p.queue
        if (q.isEmpty()) return
        playCurrent(q, minOf(p.queueIndex + 1, q.size - 1))
    }

    fun playPrev() {
        val p = _player.value
        val q = p.queue
        if (q.isEmpty()) return
        playCurrent(q, maxOf(p.queueIndex - 1, 0))
    }

    fun togglePlayPause() {
        if (_player.value.isPlaying) playerCore.pause() else playerCore.resume()
    }

    fun stopPlayback() {
        saveCurrentResume()
        playerCore.release()
        playerCore.stopService(app)
        _player.value = _player.value.copy(isPlaying = false, positionMs = 0L)
    }

    fun seekTo(ms: Long) {
        playerCore.seekTo(ms)
        _player.value = _player.value.copy(positionMs = ms)
    }

    fun toggleFavorite(song: MusicResult) {
        viewModelScope.launch {
            val existing = favoriteDao.find("MUSIC", song.id)
            if (existing == null) {
                favoriteDao.upsert(
                    FavoriteEntity(
                        id = UUID.randomUUID().toString(),
                        module = "MUSIC",
                        sourceId = song.source,
                        contentId = song.id,
                        title = song.title,
                        subInfo = song.artist,
                        favoriteTime = System.currentTimeMillis()
                    )
                )
            } else {
                favoriteDao.remove(existing.id)
            }
        }
    }

    private fun playCurrent(queue: List<MusicResult>, index: Int) {
        val song = queue.getOrNull(index) ?: return
        viewModelScope.launch {
            val url = runCatching { musicEngine.getMusicUrl(song, "standard") }.getOrDefault("")
            if (url.isBlank()) {
                _player.value = _player.value.copy(isPlaying = false)
                return@launch
            }
            val resumePos = playHistoryDao.findByContent(song.source, song.id)?.positionMs ?: 0L
            val item = PlayMediaItem(
                url = url,
                name = song.title,
                seekPositionMs = resumePos
            )
            _player.value = _player.value.copy(
                current = item,
                queueIndex = index,
                isPlaying = true
            )
            // 播放器由 Activity 侧 initialize(context)；ViewModel 仅切换媒体项
            runCatching { playerCore.switchUrl(item) }
            // 拉取歌词
            val lyricText = runCatching { musicEngine.getLyric(song) }.getOrDefault("")
            _player.value = _player.value.copy(lyric = LrcParser.parse(lyricText))
        }
    }

    private fun saveCurrentResume() {
        val p = _player.value
        val idx = p.queue.getOrNull(p.queueIndex) ?: return
        if (p.positionMs > 0L) {
            viewModelScope.launch {
                playHistoryDao.upsert(
                    PlayHistoryEntity(
                        id = "${idx.source}:${idx.id}",
                        sourceId = idx.source,
                        contentId = idx.id,
                        title = idx.title,
                        positionMs = p.positionMs,
                        module = "MUSIC",
                        updated = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
