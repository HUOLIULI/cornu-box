package com.aggregator.shell.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregator.shell.core.data.local.FavoriteDao
import com.aggregator.shell.core.data.local.PlayHistoryDao
import com.aggregator.shell.core.data.local.SearchHistoryDao
import com.aggregator.shell.core.data.local.entity.FavoriteEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.media.danmaku.DanmakuItem
import com.aggregator.shell.core.media.danmaku.DanmakuSource
import com.aggregator.shell.core.media.epg.EpgProgram
import com.aggregator.shell.core.media.epg.EpgProvider
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.media.player.PlayerState
import com.aggregator.shell.core.media.player.PlaybackResilience
import com.aggregator.shell.core.search.SearchAggregator
import com.aggregator.shell.core.source.api.PlayResult
import com.aggregator.shell.core.source.api.VideoDetail
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.engine.SourceBootstrap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import android.app.Application

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
    private val playerCore: PlayerCore,
    private val danmakuSource: DanmakuSource,
    private val epgProvider: EpgProvider,
    private val searchAggregator: SearchAggregator,
    private val favoriteDao: FavoriteDao,
    private val playHistoryDao: PlayHistoryDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val app: Application
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

    // ---------- 收藏 ----------
    private val _favorites = MutableStateFlow(emptyList<FavoriteEntity>())
    val favorites: StateFlow<List<FavoriteEntity>> = _favorites.asStateFlow()

    // ---------- 搜索历史 ----------
    private val _searchHistory = MutableStateFlow(emptyList<SearchHistoryEntity>())
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = _searchHistory.asStateFlow()

    /** 当前直播频道的 EPG 节目单快照（正在播 + 即将播 + 频道信息）。 */
    private val _epg = MutableStateFlow(com.aggregator.shell.core.media.epg.EpgSnapshot(null, null, emptyList()))
    val epg: StateFlow<com.aggregator.shell.core.media.epg.EpgSnapshot> = _epg.asStateFlow()

    // ---------- 播放韧性：多线路回退 ----------
    /** 当前线路下标（1 基，对齐 episodes Map 迭代顺序）。 */
    private var currentLineIdx = 1
    /** 当前集下标（1 基）。 */
    private var currentEpIdx = 1
    /** 本轮已尝试过的 "线路-集" 组合，成功 Ready 后清空，避免反复回退死循环。 */
    private val triedLines = mutableSetOf<String>()

    fun refresh(keyword: String = "演示") {
        viewModelScope.launch {
            _loadingResults.value = true
            val agg = runCatching { searchAggregator.searchVideos(keyword) }.getOrNull()
            // 映射回 VideoResult 复用现有列表 UI
            _results.value = agg?.items?.map {
                VideoResult(
                    id = it.contentId,
                    title = it.title,
                    coverUrl = it.coverUrl,
                    type = it.type,
                    year = it.year,
                    sourceKey = it.sourceKey
                )
            } ?: emptyList()
            _loadingResults.value = false
        }
    }

    /** 收藏 / 取消收藏某内容。 */
    fun toggleFavorite(contentId: String, sourceId: String, title: String, subInfo: String = "") {
        viewModelScope.launch {
            val existing = favoriteDao.find("VIDEO", contentId)
            if (existing == null) {
                favoriteDao.upsert(
                    FavoriteEntity(
                        id = UUID.randomUUID().toString(),
                        module = "VIDEO",
                        sourceId = sourceId,
                        contentId = contentId,
                        title = title,
                        subInfo = subInfo,
                        favoriteTime = System.currentTimeMillis()
                    )
                )
            } else {
                favoriteDao.remove(existing.id)
            }
        }
    }

    /** 续播：取该内容上次播放位置，无记录则从头。 */
    suspend fun resumePositionFor(sourceId: String, contentId: String): Long =
        playHistoryDao.findByContent(sourceId, contentId)?.positionMs ?: 0L

    /** 保存断点：记录当前播放位置供下次续播。 */
    fun saveResumePosition(sourceId: String, contentId: String, title: String, positionMs: Long) {
        viewModelScope.launch {
            if (positionMs > 0L) {
                playHistoryDao.upsert(
                    PlayHistoryEntity(
                        id = "$sourceId:$contentId",
                        sourceId = sourceId,
                        contentId = contentId,
                        title = title,
                        positionMs = positionMs,
                        module = "VIDEO",
                        updated = System.currentTimeMillis()
                    )
                )
            }
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

    /** 加载 IPTV 列表：优先读 Room 直播源表（用户导入的 IPTV），空则回退内置演示。 */
    fun loadLives() {
        viewModelScope.launch {
            _loadingLives.value = true
            val tvBoxEngine = videoEngine as? com.aggregator.shell.core.source.engine.TvBoxEngine
            val lives = runCatching { tvBoxEngine?.liveChannels()?.mapNotNull { def ->
                if (def.url.isBlank()) null
                else LiveChannel(
                    name = def.name,
                    url = def.url,
                    group = def.group,
                    isHls = def.url.endsWith(".m3u8", true),
                    epg = def.epg
                )
            } }.getOrNull()
            _lives.value = lives ?: parseLives(com.aggregator.shell.core.source.engine.SourceBootstrap.defaultTvBoxJson())
            _loadingLives.value = false
        }
    }

    /** 短剧切集：组装 PlayMediaItem 并切换播放器。 */
    fun switchDrama(index: Int) {
        val d = _dramas.value.getOrNull(index) ?: return
        val item = PlayMediaItem(url = d.url, name = d.title, isHls = d.isHls)
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
                epgProvider.epgFor(channel.epg, channelId = channel.name)
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
            val resumePos = resumePositionFor(item.sourceKey, item.id)
            currentLineIdx = 1
            currentEpIdx = 1
            triedLines.clear()
            val media = mediaItemFor(item, play, detail, resumePos)
            _play.value = _play.value.copy(
                loading = false,
                detail = detail,
                current = media,
                episodes = detail.episodes,
                episodeCount = detail.episodes.values.firstOrNull()?.size ?: 0,
                error = if (media == null) "无可用播放地址" else null
            )
            if (detail.title.isNotBlank()) loadDanmaku(detail.title, 1)
        }
    }

    /** 切换线路/集：flag 形如 "lineIndex-epIndex"（1 基）。用户主动切换会重置回退记录。 */
    fun switchEpisode(lineIdx: Int, epIdx: Int) {
        switchEpisodeInternal(lineIdx, epIdx, isFallback = false)
    }

    private fun switchEpisodeInternal(lineIdx: Int, epIdx: Int, isFallback: Boolean) {
        viewModelScope.launch {
            val d = _play.value.detail ?: return@launch
            val play = runCatching {
                videoEngine.getPlayUrl(d.id, "${lineIdx + 1}-${epIdx + 1}")
            }.getOrNull() ?: return@launch
            currentLineIdx = lineIdx + 1
            currentEpIdx = epIdx + 1
            if (!isFallback) triedLines.clear()
            val item = PlayMediaItem(
                url = play.url,
                headers = play.headers,
                name = "${d.title} · ${epIdx + 1}",
                isHls = play.url.endsWith(".m3u8", true)
            )
            _play.value = _play.value.copy(current = item, error = null)
            runCatching { playerCore.switchUrl(item) }
            loadDanmaku(d.title, epIdx + 1)
        }
    }

    /**
     * 播放韧性：线路播放失败（[PlayerState.Error]）时自动回退到其它线路的同一集，
     * 全部线路失败才提示。对标 PeekPro 的多线路切换。
     */
    private fun fallbackToNextLine() {
        val d = _play.value.detail ?: return
        val lines = d.episodes.values.toList()
        if (lines.size <= 1) return
        triedLines.add("$currentLineIdx-$currentEpIdx")
        val candidates = (1..lines.size).mapNotNull { ln ->
            val url = lines[ln - 1].getOrNull(currentEpIdx - 1)
            if (url != null && "$ln-$currentEpIdx" !in triedLines) ln else null
        }
        if (candidates.isEmpty()) {
            _play.value = _play.value.copy(error = "所有线路均不可用")
            return
        }
        // 用 PlayUrlPreflight 预检候选线路，挑第一条真可用的；预检失败则直接按顺序回退。
        viewModelScope.launch {
            val idx = com.aggregator.shell.core.media.player.PlayUrlPreflight.firstAvailable(
                candidates.map { ln -> lines[ln - 1][currentEpIdx - 1] to emptyMap() }
            )
            val next = idx?.let { candidates[it] } ?: candidates.first()
            switchEpisodeInternal(next - 1, currentEpIdx - 1, isFallback = true)
        }
    }

    fun exitPlayback() {
        saveCurrentResumePosition()
        playerCore.release()
        playerCore.stopService(app)
        _play.value = PlayUiState()
        _danmaku.value = emptyList()
        _epg.value = com.aggregator.shell.core.media.epg.EpgSnapshot(null, null, emptyList())
    }

    /** 退出播放前把当前断点写回 Room，供下次续播。 */
    private fun saveCurrentResumePosition() {
        val d = _play.value.detail ?: return
        if (d.title.isBlank()) return
        saveResumePosition(d.sourceKey, d.id, d.title, playerCore.currentPositionMs())
    }

    private fun mediaItemFor(
        item: VideoResult,
        play: PlayResult?,
        detail: VideoDetail,
        seekPositionMs: Long = 0L
    ): PlayMediaItem? {
        val url = play?.url?.takeIf { it.isNotBlank() } ?: return null
        return PlayMediaItem(
            url = url,
            headers = play?.headers ?: emptyMap(),
            name = detail.title.ifBlank { item.title },
            isHls = url.endsWith(".m3u8", true),
            seekPositionMs = seekPositionMs
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
        // 镜像播放器状态到 PlayUiState，驱动播放页 UI
        viewModelScope.launch {
            playerCore.state.collect { state ->
                _play.value = _play.value.copy(playerState = state)
                when (state) {
                    PlayerState.Ready -> triedLines.clear()
                    PlayerState.Error -> fallbackToNextLine()
                    else -> {}
                }
            }
        }
        // 自动连播：非直播流播完自动切下一集；直播流掉出窗口自动重拉当前频道
        viewModelScope.launch {
            playerCore.playbackEnded.collect {
                val detail = _play.value.detail
                if (detail == null) return@collect
                val lines = detail.episodes.values
                if (lines.isEmpty()) return@collect
                val epList = lines.firstOrNull() ?: return@collect
                val currentEp = currentEpIdx
                val nextEp = currentEp + 1
                if (nextEp <= epList.size) {
                    // 自动连播：同线路下一集
                    switchEpisodeInternal(currentLineIdx - 1, nextEp - 1, isFallback = false)
                } else {
                    // 已是最后一集：若为直播/单集则重拉（live 窗口掉出）
                    if (PlaybackResilience.isLiveLike(epList.firstOrNull().orEmpty())) {
                        switchEpisodeInternal(currentLineIdx - 1, currentEp - 1, isFallback = false)
                    }
                }
            }
        }
        viewModelScope.launch {
            favoriteDao.byModule("VIDEO").collectLatest { _favorites.value = it }
        }
        viewModelScope.launch {
            searchHistoryDao.recent().collectLatest { _searchHistory.value = it }
        }
        refresh()
        loadDramas()
        loadLives()
    }
}
