package com.aggregator.shell.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.media.player.PlayerState
import com.aggregator.shell.core.source.api.PlayResult
import com.aggregator.shell.core.source.api.VideoDetail
import com.aggregator.shell.core.source.api.VideoEngine
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.source.engine.SourceBootstrap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
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

/** IPTV 直播条目。 */
data class LiveChannel(val name: String, val url: String, val group: String, val isHls: Boolean)

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val videoEngine: VideoEngine,
    private val playerCore: PlayerCore
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

    fun refresh(keyword: String = "演示") {
        viewModelScope.launch {
            _loadingResults.value = true
            val list = runCatching { videoEngine.search(keyword, 1) }.getOrDefault(emptyList())
            _results.value = list
            _loadingResults.value = false
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
        _play.value = _play.value.copy(current = item, error = null, loading = false)
        runCatching { playerCore.switchUrl(item) }
    }

    /** IPTV 选台：切换直播流。 */
    fun switchLive(channel: LiveChannel) {
        val item = PlayMediaItem(
            url = channel.url,
            name = channel.name,
            isHls = channel.isHls
        )
        _play.value = _play.value.copy(current = item, error = null, loading = false)
        runCatching { playerCore.switchUrl(item) }
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
            _play.value = _play.value.copy(
                loading = false,
                detail = detail,
                current = media,
                episodes = detail.episodes,
                episodeCount = detail.episodes.values.firstOrNull()?.size ?: 0,
                error = if (media == null) "无可用播放地址" else null
            )
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
            _play.value = _play.value.copy(current = item, error = null)
            runCatching { playerCore.switchUrl(item) }
        }
    }

    fun exitPlayback() {
        playerCore.release()
        _play.value = PlayUiState()
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
                isHls = url.endsWith(".m3u8", true)
            )
        }
    }.getOrDefault(emptyList())

    init {
        // 镜像播放器状态到 PlayUiState，驱动播放页 UI
        viewModelScope.launch {
            playerCore.state.collect { state ->
                _play.value = _play.value.copy(playerState = state)
            }
        }
        refresh()
        loadDramas()
        loadLives()
    }
}
