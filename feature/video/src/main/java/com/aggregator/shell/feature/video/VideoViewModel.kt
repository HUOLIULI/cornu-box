package com.aggregator.shell.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregator.shell.core.data.local.FavoritesDao
import com.aggregator.shell.core.data.local.PlayHistoryDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.FavoritesEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.media.danmaku.DanmakuItem
import com.aggregator.shell.core.media.danmaku.DanmakuSource
import com.aggregator.shell.core.media.epg.EpgProvider
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.media.player.PlayerState
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aggregator.shell.core.data.di.appDataStore
import com.aggregator.shell.core.source.api.PlayResult
import com.aggregator.shell.core.source.api.VideoDetail
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.engine.SourceBootstrap
import com.aggregator.shell.core.source.search.VideoSearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.text.format
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * 点播播放 UI 状态：列表项点击后驱动 [VideoActivity] 渲染 [PlayerSurface]。
 *
 * 点击卡片 -> [loadDetail] 取剧集 -> [loadPlayUrl] 取首集播放地址 -> 组装 [PlayMediaItem]，
 * 播放器生命周期由注入的 [PlayerCore] 承载（initialize/prepare/attach 在 Composable 内完成）。
 */
data class PlayUiState(
    val current: PlayMediaItem? = null,
    val detail: VideoDetail? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 全部线路：线路名 -> 该线路各集 URL，供用户切线/选集。 */
    val episodes: Map<String, List<String>> = emptyMap(),
    /** 当前选中线路下的集数。 */
    val episodeCount: Int = 0,
    /** 当前播放器状态（镜像自 [PlayerCore.state]）。 */
    val playerState: PlayerState = PlayerState.Idle
)

/** 短剧（竖屏上下滑）单集条目。 */
data class DramaEpisode(val title: String, val url: String, val isHls: Boolean)

/** IPTV 直播条目。epg 为该频道的 XMLTV EPG 源 URL（可空，空则走演示 EPG）。 */
data class LiveChannel(
    val name: String,
    val url: String,
    val group: String,
    val isHls: Boolean,
    val epg: String = ""
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val videoEngine: VideoEngine,
    private val videoSearchRepository: VideoSearchRepository,
    private val playerCore: PlayerCore,
    private val danmakuSource: DanmakuSource,
    private val epgProvider: EpgProvider,
    private val playHistoryDao: PlayHistoryDao,
    private val favoritesDao: FavoritesDao,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    private val _play = MutableStateFlow(PlayUiState())
    val play: StateFlow<PlayUiState> = _play.asStateFlow()

    /** 点播结果流：供列表页展示。 */
    private val _results = MutableStateFlow(emptyList<VideoResult>())
    val results: StateFlow<List<VideoResult>> = _results.asStateFlow()

    private val _loadingResults = MutableStateFlow(false)
    val loadingResults: StateFlow<Boolean> = _loadingResults.asStateFlow()

    // ---------- 短剧（竖屏上下滑）----------
    private val _dramas = MutableStateFlow(emptyList<DramaEpisode>())
    val dramas: StateFlow<List<DramaEpisode>> = _dramas.asStateFlow()

    private val _loadingDramas = MutableStateFlow(false)
    val loadingDramas: StateFlow<Boolean> = _loadingDramas.asStateFlow()

    // ---------- IPTV ----------
    private val _lives = MutableStateFlow(emptyList<LiveChannel>())
    val lives: StateFlow<List<LiveChannel>> = _lives.asStateFlow()

    private val _loadingLives = MutableStateFlow(false)
    val loadingLives: StateFlow<Boolean> = _loadingLives.asStateFlow()

    // ---------- 弹幕 / EPG ----------
    /** 当前播放项的弹幕（由 [DanmakuSource] 按剧集生成，替换原静态 remember）。 */
    private val _danmaku = MutableStateFlow(emptyList<DanmakuItem>())
    val danmaku: StateFlow<List<DanmakuItem>> = _danmaku.asStateFlow()

    /** 当前直播频道的 EPG 节目单快照（正在播 + 即将播 + 频道信息）。 */
    private val _epg = MutableStateFlow(com.aggregator.shell.core.media.epg.EpgSnapshot(null, null, emptyList()))
    val epg: StateFlow<com.aggregator.shell.core.media.epg.EpgSnapshot> = _epg.asStateFlow()

    // ---------- 播放历史 ----------
    private val _playHistory = MutableStateFlow(emptyList<PlayHistoryEntity>())
    val playHistory: StateFlow<List<PlayHistoryEntity>> = _playHistory.asStateFlow()

    // ---------- 收藏 ----------
    private val _favorites = MutableStateFlow(emptyList<FavoritesEntity>())
    val favorites: StateFlow<List<FavoritesEntity>> = _favorites.asStateFlow()

    /** 搜索历史（最近 20 条，按使用时间倒序） */
    private val _searchHistory = MutableStateFlow(emptyList<SearchHistoryEntity>())
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = _searchHistory.asStateFlow()

    // ---------- 搜索历史 ----------

    // ---------- IPTV 频道收藏 + 换台记忆 ----------
    private val _liveFavorites = MutableStateFlow(emptyList<FavoritesEntity>())
    val liveFavorites: StateFlow<List<FavoritesEntity>> = _liveFavorites.asStateFlow()

    /** 记忆上次选中的直播频道 URL，进入 IPTV Tab 时自动恢复（持久化到 DataStore）。 */
    private var lastLiveChannelUrl: String = ""
    private val keyLastLiveChannel = stringPreferencesKey("last_live_channel")

    // ---------- 播放进度 / 自动连播 ----------
    private var currentLineIdx: Int = 0
    private var currentEpIdx: Int = 0
    private var dramaIndex: Int = 0
    private var progressSaveJob: Job? = null

    // ---------- 片头片尾跳过标记 ----------
    private val _skipIntroUntilMs = MutableStateFlow(0L)
    val skipIntroUntilMs: StateFlow<Long> = _skipIntroUntilMs.asStateFlow()

    private val _skipOutroStartMs = MutableStateFlow(0L)
    val skipOutroStartMs: StateFlow<Long> = _skipOutroStartMs.asStateFlow()

    /** 全局 toast 提示（UI 层监听后自动消失） */
    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    fun setSkipIntro(ms: Long) {
        _skipIntroUntilMs.value = ms
    }

    fun setSkipOutro(startMs: Long) {
        _skipOutroStartMs.value = startMs
    }

    /** 返回当前应跳过的目标位置（片头结束 or 片尾开始），0 表示无需跳过。 */
    fun getSkipTarget(currentMs: Long, durationMs: Long): Long {
        val introEnd = _skipIntroUntilMs.value
        if (introEnd > 0 && currentMs < introEnd) return introEnd
        val outroStart = _skipOutroStartMs.value
        if (outroStart > 0 && durationMs > 0 && currentMs >= outroStart) return outroStart
        return 0L
    }

    fun refresh(keyword: String = "演示") {
        viewModelScope.launch {
            if (keyword.isNotBlank()) {
                searchHistoryDao.upsert(
                    SearchHistoryEntity(
                        query = keyword,
                        module = "video",
                        lastUsed = System.currentTimeMillis()
                    )
                )
            }
            _loadingResults.value = true
            val items = runCatching { videoSearchRepository.search(keyword, 1) }.getOrDefault(emptyList())
            _results.value = VideoSearchRepository.toVideoResults(items)
            _loadingResults.value = false
        }
    }

    fun getSearchHistory() {
        viewModelScope.launch {
            searchHistoryDao.byModule("video").collect { _searchHistory.value = it }
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            searchHistoryDao.remove("video", query)
            _searchHistory.value = searchHistoryDao.byModule("video").first()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearByModule("video")
            _searchHistory.value = emptyList()
        }
    }

    /** 加载短剧列表：取内置演示源首线路各集，竖屏上下滑。 */
    fun loadDramas() {
        viewModelScope.launch {
            _loadingDramas.value = true
            val detail = runCatching { videoEngine.getDetail("demo-1") }.getOrNull()
            val firstLine = detail?.episodes?.values?.firstOrNull() ?: emptyList()
            _dramas.value = firstLine.mapIndexed { i, u ->
                DramaEpisode(
                    title = "短剧第 ${i + 1} 集",
                    url = u,
                    isHls = u.endsWith(".m3u8", true)
                )
            }
            _loadingDramas.value = false
        }
    }

    /** 加载 IPTV 列表：解析内置 TVBox lives 配置。 */
    fun loadLives() {
        viewModelScope.launch {
            _loadingLives.value = true
            _lives.value = parseLives(SourceBootstrap.defaultTvBoxJson())
            _loadingLives.value = false
        }
    }

    /** 短剧切集：组装 PlayMediaItem 并切换播放器。 */
    fun switchDrama(index: Int) {
        val d = _dramas.value.getOrNull(index) ?: return
        val item = PlayMediaItem(url = d.url, name = d.title, isHls = d.isHls)
        dramaIndex = index
        _play.value = _play.value.copy(current = item, error = null, loading = false)
        runCatching { playerCore.switchUrl(item) }
        loadDanmaku(d.title, index + 1)
    }

    /** IPTV 选台：切换直播流 + 加载 EPG。 */
    fun switchLive(channel: LiveChannel) {
        val item = PlayMediaItem(
            url = channel.url,
            name = channel.name,
            isHls = channel.isHls
        )
        _play.value = _play.value.copy(current = item, error = null, loading = false)
        runCatching { playerCore.switchUrl(item) }
        loadEpg(channel)
        // 添加到播放历史
        addToHistory(item, "live", channel.name)
        rememberLiveChannel(channel.url)
    }

    /** 点播切集后刷新弹幕。 */
    fun onEpisodesSwitched(title: String, epIdx: Int) {
        loadDanmaku(title, epIdx + 1)
    }

    /** 加载剧集弹幕（走 [DanmakuSource]，演示源按标题生成）。 */
    private fun loadDanmaku(title: String, episode: Int) {
        viewModelScope.launch {
            val id = runCatching { danmakuSource.searchEpisode(title, episode) }.getOrNull()
            _danmaku.value = if (id != null) {
                runCatching { danmakuSource.loadDanmaku(id) }.getOrDefault(emptyList())
            } else emptyList()
        }
    }

    /** 加载直播频道 EPG：优先真实 XMLTV 源，拉取/解析失败回退演示。 */
    private fun loadEpg(channel: LiveChannel) {
        viewModelScope.launch {
            _epg.value = runCatching {
                epgProvider.epgFor(channel.epg, channelId = "live-demo")
            }.getOrDefault(com.aggregator.shell.core.media.epg.EpgSnapshot(null, null, emptyList()))
        }
    }

    /** 列表项被点击：拉详情 + 首集播放地址，进入播放页。 */
    fun onItemClicked(item: VideoResult) {
        viewModelScope.launch {
            _play.value = _play.value.copy(loading = true, error = null)
            val detail = runCatching { videoEngine.getDetail(item.id) }.getOrNull()
            if (detail == null) {
                _play.value = _play.value.copy(loading = false, error = "详情获取失败：${item.id}")
                return@launch
            }
            val play = runCatching { videoEngine.getPlayUrl(item.id, "1-1") }.getOrNull()
            val media = mediaItemFor(item, play, detail)
            currentLineIdx = 0
            currentEpIdx = 0
            _play.value = _play.value.copy(
                loading = false,
                detail = detail,
                current = media,
                episodes = detail.episodes,
                episodeCount = detail.episodes.values.firstOrNull()?.size ?: 0,
                error = if (media == null) "无可用播放地址" else null
            )
            if (media != null) {
                addToHistory(media, "video", detail.title)
                startProgressSave()
            }
            if (detail.title.isNotBlank()) loadDanmaku(detail.title, 1)
        }
    }

    /** 切换线路/集：flag 形如 "lineIndex-epIndex"（1 基）。 */
    fun switchEpisode(lineIdx: Int, epIdx: Int) {
        viewModelScope.launch {
            val d = _play.value.detail ?: return@launch
            val play = runCatching {
                videoEngine.getPlayUrl(d.id, "${lineIdx + 1}-${epIdx + 1}")
            }.getOrNull() ?: return@launch
            val item = PlayMediaItem(
                url = play.url,
                headers = play.headers,
                name = "${d.title} · ${epIdx + 1}",
                isHls = play.url.endsWith(".m3u8", true)
            )
            currentLineIdx = lineIdx
            currentEpIdx = epIdx
            _play.value = _play.value.copy(current = item, error = null)
            runCatching { playerCore.switchUrl(item) }
            loadDanmaku(d.title, epIdx + 1)
        }
    }

    fun exitPlayback() {
        progressSaveJob?.cancel()
        progressSaveJob = null
        playerCore.setOnCompletionListener(null)
        playerCore.release()
        currentLineIdx = 0
        currentEpIdx = 0
        dramaIndex = 0
        _play.value = PlayUiState()
        _danmaku.value = emptyList()
        _epg.value = com.aggregator.shell.core.media.epg.EpgSnapshot(null, null, emptyList())
    }

    /** 添加播放记录到历史 */
    private fun addToHistory(item: PlayMediaItem, module: String, title: String, positionMs: Long = 0L) {
        val history = PlayHistoryEntity(
            id = "hist_${item.url.hashCode()}",
            sourceId = item.url,
            contentId = item.url,
            title = title,
            positionMs = positionMs,
            module = module,
            updated = System.currentTimeMillis()
        )
        viewModelScope.launch {
            playHistoryDao.upsert(history)
        }
    }

    /** 获取播放历史 */
    fun getPlayHistory(module: String = "video") {
        viewModelScope.launch {
            playHistoryDao.byModule(module).collect { history ->
                _playHistory.value = history
            }
        }
    }

    /** 获取收藏列表 */
    fun getFavorites(module: String = "video") {
        viewModelScope.launch {
            favoritesDao.byModule(module).collect { favs ->
                _favorites.value = favs
            }
        }
    }

    /** 添加到收藏 */
    fun addToFavorites(title: String, coverUrl: String = "", category: String = "videos") {
        viewModelScope.launch {
            val favorite = FavoritesEntity(
                id = "fav_${UUID.randomUUID().toString()}",
                sourceId = "",
                contentId = UUID.randomUUID().toString(),
                title = title,
                coverUrl = coverUrl,
                module = "video",
                addedTime = System.currentTimeMillis(),
                category = category
            )
            favoritesDao.upsert(favorite)
        }
    }

    /** 从收藏移除 */
    fun removeFromFavorites(id: String) {
        viewModelScope.launch {
            favoritesDao.remove(id)
        }
    }

    /** 清空当前模块的收藏 */
    fun clearFavorites(module: String = "video") {
        viewModelScope.launch {
            favoritesDao.clearByModule(module)
        }
    }

    // ---------- IPTV 频道收藏 ----------

    fun toggleLiveFavorite(channel: LiveChannel) {
        viewModelScope.launch {
            val existing = favoritesDao.byCategory("live").first()
                .find { it.contentId == channel.url }
            if (existing != null) {
                favoritesDao.remove(existing.id)
            } else {
                favoritesDao.upsert(
                    FavoritesEntity(
                        id = "fav_live_${channel.url.hashCode()}",
                        sourceId = "",
                        contentId = channel.url,
                        title = channel.name,
                        coverUrl = "",
                        module = "live",
                        addedTime = System.currentTimeMillis(),
                        category = "live"
                    )
                )
            }
            _liveFavorites.value = favoritesDao.byCategory("live").first()
        }
    }

    fun isLiveFavorite(url: String): Boolean {
        return _liveFavorites.value.any { it.contentId == url }
    }

    private fun refreshLiveFavorites() {
        viewModelScope.launch {
            favoritesDao.byCategory("live").collect { _liveFavorites.value = it }
        }
    }

    // ---------- 换台记忆 ----------

    fun restoreLastLiveChannel() {
        val saved = lastLiveChannelUrl
        if (saved.isBlank()) return
        val ch = _lives.value.find { it.url == saved } ?: return
        switchLive(ch)
    }

    private fun rememberLiveChannel(url: String) {
        lastLiveChannelUrl = url
        viewModelScope.launch {
            appDataStore.edit { prefs ->
                prefs[keyLastLiveChannel] = url
            }
        }
    }

    private fun loadLastLiveChannel() {
        viewModelScope.launch {
            val prefs = appDataStore.data.first()
            lastLiveChannelUrl = prefs[keyLastLiveChannel] ?: ""
        }
    }

    /** 从历史恢复播放位置（同一 URL 的进度由 [startProgressSave] 持续覆盖） */
    fun resumePlayback(historyId: String) {
        viewModelScope.launch {
            playHistoryDao.byId(historyId)?.let { h ->
                currentLineIdx = 0
                currentEpIdx = 0
                dramaIndex = 0
                val item = PlayMediaItem(
                    url = h.contentId,
                    name = h.title,
                    isHls = h.contentId.endsWith(".m3u8", true)
                )
                _play.value = _play.value.copy(current = item, error = null)
                runCatching { playerCore.prepare(item) }
                if (h.positionMs > 0) {
                    playerCore.seekTo(h.positionMs)
                    _toast.value = "已从 ${(h.positionMs / 1000L).div(60).toString() + ":" + "%02d".format((h.positionMs % 60_000L) / 1000L)} 继续播放"
                } else {
                    _toast.value = "开始播放"
                }
                startProgressSave()
            }
        }
    }

    /** 清除当前 toast（由 UI 层在显示完毕后调用） */
    fun clearToast() {
        _toast.value = ""
    }

    /** 启动定期保存播放进度（每 5 秒） */
    private fun startProgressSave() {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                val item = _play.value.current ?: break
                val pos = playerCore.getCurrentPositionMs()
                if (pos > 0) {
                    addToHistory(item, "video", item.name, positionMs = pos)
                }
            }
        }
    }

    private fun mediaItemFor(
        item: VideoResult,
        play: PlayResult?,
        detail: VideoDetail
    ): PlayMediaItem? {
        val url = play?.url?.takeIf { it.isNotBlank() } ?: return null
        return PlayMediaItem(
            url = url,
            headers = play?.headers ?: emptyMap(),
            name = detail.title.ifBlank { item.title },
            isHls = url.endsWith(".m3u8", true)
        )
    }

    private fun parseLives(tvBoxJson: String): List<LiveChannel> = runCatching {
        val obj = JSONObject(tvBoxJson)
        val lives = obj.optJSONArray("lives") ?: return emptyList()
        (0 until lives.length()).mapNotNull { i ->
            val l = lives.getJSONObject(i)
            val url = l.optString("url")
            if (url.isBlank()) null
            else LiveChannel(
                name = l.optString("name", "直播 $i"),
                url = url,
                group = l.optString("group", ""),
                isHls = url.endsWith(".m3u8", true),
                epg = l.optString("epg", "")
            )
        }
    }.getOrDefault(emptyList())

    init {
        // 镜像播放器状态到 PlayUiState，驱动播放页 UI + 多线路自动切换
        viewModelScope.launch {
            playerCore.state.collect { state ->
                val prev = _play.value.playerState
                _play.value = _play.value.copy(playerState = state)
                if (state == PlayerState.Error && prev != PlayerState.Error) {
                    onPlaybackError()
                }
            }
        }
        // 自动连播：剧集/短剧播完自动切下一集
        playerCore.setOnCompletionListener { onPlaybackEnded() }
        refresh()
        loadDramas()
        loadLives()
        getPlayHistory()
        getFavorites()
        refreshLiveFavorites()
        loadLastLiveChannel()
        getSearchHistory()
    }

    private var lastPlaybackErrorAtMs = 0L

    /** 播放失败回调：点播/短剧场景自动尝试下一线路（遍历全部线路，每集尝试一次）。
     *  同一直播线路在 3s 内重复失败不再切换，防止末线路反复重试。 */
    private fun onPlaybackError() {
        if (_play.value.detail == null) return
        val now = System.currentTimeMillis()
        if (now - lastPlaybackErrorAtMs < 3_000L) return
        lastPlaybackErrorAtMs = now
        val lineKeys = _play.value.episodes.keys.toList()
        if (lineKeys.isEmpty()) return
        if (currentLineIdx < lineKeys.size - 1) {
            switchEpisode(currentLineIdx + 1, currentEpIdx)
            _toast.value = "线路切换失败，正在尝试下一线路…"
        } else {
            _play.value = _play.value.copy(
                error = "播放失败：已遍历全部 ${lineKeys.size} 条线路"
            )
            _toast.value = "所有线路均失败，请检查网络"
        }
    }

    /** 播放完成回调：短剧优先自动跳下一集，否则剧集自动切下一集。 */
    private fun onPlaybackEnded() {
        val hasDetail = _play.value.detail != null
        if (!hasDetail && _dramas.value.isNotEmpty()) {
            val next = dramaIndex + 1
            if (next < _dramas.value.size) {
                switchDrama(next)
            }
            return
        }
        val detail = _play.value.detail ?: return
        val lines = detail.episodes
        val lineKeys = lines.keys.toList()
        if (currentLineIdx < lineKeys.size - 1) {
            val nextLine = currentLineIdx + 1
            switchEpisode(nextLine, 0)
            return
        }
        val epCount = lines[lineKeys[currentLineIdx]]?.size ?: 0
        if (currentEpIdx < epCount - 1) {
            switchEpisode(currentLineIdx, currentEpIdx + 1)
            return
        }
        // 最后一集播完，停在本集
    }
}
