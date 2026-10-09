package com.aggregator.shell.feature.video

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.aggregator.shell.core.media.danmaku.DanmakuItem
import com.aggregator.shell.core.media.epg.EpgSnapshot
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.ui.theme.AppTheme
import com.aggregator.shell.feature.video.ui.PlayerSurface
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class VideoActivity : ComponentActivity() {

    @Inject
    lateinit var playerCore: PlayerCore

    private val vm: VideoViewModel by viewModels()

    companion object {
        /** 从「我的」追剧夹拉起指定内容的播放（按 contentId 定位）。 */
        fun openContent(ctx: android.content.Context, favorite: com.aggregator.shell.core.data.local.entity.FavoriteEntity) {
            ctx.startActivity(
                android.content.Intent(ctx, VideoActivity::class.java)
                    .putExtra("extra_content_id", favorite.contentId)
                    .putExtra("extra_source_id", favorite.sourceId)
                    .putExtra("extra_title", favorite.title)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerCore.initialize(this)
        val extraContentId = intent.getStringExtra("extra_content_id")
        val extraSourceId = intent.getStringExtra("extra_source_id")
        val extraTitle = intent.getStringExtra("extra_title")
        setContent {
            AppTheme {
                var tabIndex by remember { mutableIntStateOf(0) }
                // 短剧 Tab 切竖屏，离开恢复；点播/IPTV 保持默认方向
                androidx.compose.runtime.DisposableEffect(tabIndex) {
                    requestedOrientation = if (tabIndex == 1) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    onDispose {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                // 从「我的」追剧夹跳转：直接打开该内容播放
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (extraContentId != null && extraSourceId != null) {
                        vm.onItemClicked(
                            com.aggregator.shell.core.source.api.VideoResult(
                                id = extraContentId,
                                title = extraTitle.orEmpty(),
                                coverUrl = "",
                                sourceKey = extraSourceId
                            )
                        )
                    }
                }
                val playState = vm.play.collectAsState()
                val results = vm.results.collectAsState()
                val loading = vm.loadingResults.collectAsState()
                val dramas = vm.dramas.collectAsState()
                val loadingDramas = vm.loadingDramas.collectAsState()
                val lives = vm.lives.collectAsState()
                val loadingLives = vm.loadingLives.collectAsState()
                val danmaku = vm.danmaku.collectAsState()
                val epg = vm.epg.collectAsState()
                val favorites = vm.favorites.collectAsState()
                val searchHistory = vm.searchHistory.collectAsState()
                val inPlayback = playState.value.current != null

                androidx.compose.runtime.LaunchedEffect(inPlayback) {
                    if (inPlayback) {
                        playerCore.startService(this@VideoActivity)
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (inPlayback) "播放" else "影视") },
                            navigationIcon = {
                                if (inPlayback) {
                                    IconButton(onClick = { vm.exitPlayback() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                    }
                                }
                            },
                            actions = {
                                if (!inPlayback) {
                                    IconButton(onClick = { vm.refresh() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                                    }
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (inPlayback) {
                            PlaybackScreen(
                                state = playState.value,
                                player = playerCore,
                                danmaku = danmaku.value,
                                epg = epg.value,
                                onSwitch = { line, ep -> vm.switchEpisode(line, ep) },
                                onFavorite = { _ ->
                                    val d = playState.value.detail
                                    if (d != null) {
                                        vm.toggleFavorite(d.id, d.sourceKey, d.title, d.desc)
                                    }
                                }
                            )
                        } else {
                            TabRow(selectedTabIndex = tabIndex) {
                                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("点播") })
                                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("短剧") })
                                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("IPTV") })
                                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }, text = { Text("追剧夹") })
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                loading.value && tabIndex == 0 -> LoadingBox()
                                tabIndex == 0 -> Column(Modifier.fillMaxSize()) {
                                    SearchHistoryChips(
                                        history = searchHistory.value,
                                        onPick = { kw -> vm.refresh(kw) }
                                    )
                                    VodoList(items = results.value) { vm.onItemClicked(it) }
                                }
                                tabIndex == 1 -> DramaPager(
                                    episodes = dramas.value,
                                    loading = loadingDramas.value,
                                    onSwitch = { i -> vm.switchDrama(i) },
                                    player = playerCore,
                                    danmaku = danmaku.value
                                )
                                tabIndex == 2 -> IptvList(
                                    channels = lives.value,
                                    loading = loadingLives.value,
                                    onSelect = { c -> vm.switchLive(c) },
                                    current = playState.value.current
                                )
                                tabIndex == 3 -> FavoriteList(
                                    favorites = favorites.value,
                                    onOpen = { fav ->
                                        val item = VideoResult(
                                            id = fav.contentId,
                                            title = fav.title,
                                            coverUrl = "",
                                            sourceKey = fav.sourceId
                                        )
                                        vm.onItemClicked(item)
                                    },
                                    onUnfavorite = { fav ->
                                        vm.toggleFavorite(fav.contentId, fav.sourceId, fav.title)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 点播列表。 */
@Composable
private fun VodoList(items: List<VideoResult>, onClick: (VideoResult) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(items) { v ->
            VideoCard(title = v.title, subtitle = "源: ${v.sourceKey} · ${v.type}") { onClick(v) }
        }
        if (items.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无结果，点击右上角刷新（内置演示源）")
                }
            }
        }
    }
}

/** 短剧竖屏上下滑：VerticalPager 逐集翻页，每页 9:16 竖屏播放器（全屏沉浸）。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DramaPager(
    episodes: List<DramaEpisode>,
    loading: Boolean,
    onSwitch: (Int) -> Unit,
    player: PlayerCore,
    danmaku: List<DanmakuItem>
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (episodes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无短剧集")
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { episodes.size })
    // 翻页完成即切集
    var lastPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            lastPage = pagerState.currentPage
            onSwitch(pagerState.currentPage)
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                VerticalVideo(ep = episodes[page], player = player, danmaku = danmaku)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${pagerState.currentPage + 1} / ${episodes.size} · ${episodes[pagerState.currentPage].title}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
        )
    }
}

/** 竖屏 9:16 视频区：挂 PlayerSurface（真实 ExoPlayer 全屏渲染 + 弹幕）。 */
@Composable
private fun VerticalVideo(ep: DramaEpisode, player: PlayerCore, danmaku: List<DanmakuItem>) {
    val current = PlayMediaItem(
        url = ep.url,
        name = ep.title,
        isHls = ep.isHls
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .padding(8.dp)
    ) {
        PlayerSurface(item = current, player = player, danmaku = danmaku)
    }
}

/** IPTV 列表 + 选中频道的 EPG 节目单。 */
@Composable
private fun IptvList(
    channels: List<LiveChannel>,
    loading: Boolean,
    onSelect: (LiveChannel) -> Unit,
    current: PlayMediaItem?
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无直播源") }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(channels) { c ->
            val selected = current?.url == c.url
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    Modifier
                        .clickable { onSelect(c) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name)
                        Text(
                            text = if (c.group.isNotBlank()) c.group else "直播",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (selected) {
                        Text(
                            "播放中",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlaybackScreen(
    state: PlayUiState,
    player: PlayerCore,
    danmaku: List<DanmakuItem>,
    epg: EpgSnapshot,
    onSwitch: (Int, Int) -> Unit,
    onFavorite: (com.aggregator.shell.core.source.api.VideoDetail) -> Unit
) {
    val current = state.current
    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current != null -> {
                PlayerSurface(item = current, player = player, danmaku = danmaku)
                if (epg.nowPlaying != null || epg.upcoming.isNotEmpty()) EpgPanel(epg)
                EpisodeSelector(state = state, onSwitch = onSwitch)
                if (state.detail != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onFavorite(state.detail!!) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("收藏到追剧夹")
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "未选择媒体")
            }
        }
    }
}

/** IPTV 选中后展示当前直播的 EPG 节目单（正在播高亮 + 即将播列表）。 */
@Composable
private fun EpgPanel(snapshot: EpgSnapshot) {
    val programs = snapshot.upcoming
    if (programs.isEmpty() && snapshot.nowPlaying == null) return
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("节目单 · ${snapshot.channel?.displayName ?: "直播"}", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().height(200.dp)) {
            items(snapshot.nowPlaying?.let { listOf(it) } ?: emptyList()) { p ->
                EpgRow(p, live = true, timeFmt = timeFmt)
            }
            items(programs) { p ->
                EpgRow(p, live = false, timeFmt = timeFmt)
            }
        }
    }
}

@Composable
private fun EpgRow(
    p: com.aggregator.shell.core.media.epg.EpgProgram,
    live: Boolean,
    timeFmt: SimpleDateFormat
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${timeFmt.format(Date(p.startTime))}-${timeFmt.format(Date(p.endTime))}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = p.title,
            style = if (live) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (live) {
            Text(" · 正在播", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EpisodeSelector(state: PlayUiState, onSwitch: (Int, Int) -> Unit) {
    if (state.episodes.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("线路与集数", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        state.episodes.entries.forEachIndexed { lineIdx, entry ->
            val line = entry.key
            val eps = entry.value
            Text(line, style = MaterialTheme.typography.labelMedium)
            Row {
                eps.forEachIndexed { epIdx, _ ->
                    Text(
                        text = "${epIdx + 1}",
                        modifier = Modifier
                            .clickable { onSwitch(lineIdx, epIdx) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 最近搜索 chips：点击可重搜（横向滚动，避免实验性 FlowRow API）。 */
@Composable
private fun SearchHistoryChips(
    history: List<com.aggregator.shell.core.data.local.entity.SearchHistoryEntity>,
    onPick: (String) -> Unit
) {
    val chips = history.filter { it.module == "VIDEO" && it.keyword.isNotBlank() }.take(8)
    if (chips.isEmpty()) return
    LazyRow(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { h ->
            androidx.compose.material3.AssistChip(
                onClick = { onPick(h.keyword) },
                label = { Text(h.keyword) }
            )
        }
    }
}

/** 追剧夹：已收藏内容，可点开续播 / 取消收藏。 */
@Composable
private fun FavoriteList(
    favorites: List<com.aggregator.shell.core.data.local.entity.FavoriteEntity>,
    onOpen: (com.aggregator.shell.core.data.local.entity.FavoriteEntity) -> Unit,
    onUnfavorite: (com.aggregator.shell.core.data.local.entity.FavoriteEntity) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("追剧夹为空：在播放页点「收藏」加入")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(favorites) { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(f) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "源: ${f.sourceId}${if (f.subInfo.isNotBlank()) " · ${f.subInfo}" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { onUnfavorite(f) }) {
                    Text("取消", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
