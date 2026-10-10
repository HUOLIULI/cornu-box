package com.aggregator.shell.feature.video

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.aggregator.shell.core.data.local.entity.FavoritesEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import com.aggregator.shell.core.media.danmaku.DanmakuItem
import com.aggregator.shell.core.media.epg.EpgSnapshot
import com.aggregator.shell.core.media.player.PlayMediaItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.source.api.VideoResult
import com.aggregator.shell.core.ui.theme.AppTheme
import com.aggregator.shell.feature.video.ui.PlayerSurface
import com.aggregator.shell.feature.video.ui.VideoGestureOverlay
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val playState = vm.play.collectAsState()
                val results = vm.results.collectAsState()
                val loading = vm.loadingResults.collectAsState()
                val dramas = vm.dramas.collectAsState()
                val loadingDramas = vm.loadingDramas.collectAsState()
        val lives = vm.lives.collectAsState()
        val loadingLives = vm.loadingLives.collectAsState()
        val liveFavorites = vm.liveFavorites.collectAsState()
        val skipIntroMs = vm.skipIntroUntilMs.collectAsState()
        val skipOutroMs = vm.skipOutroStartMs.collectAsState()
        val danmaku = vm.danmaku.collectAsState()
        val epg = vm.epg.collectAsState()
        val playHistory = vm.playHistory.collectAsState()
        val favorites = vm.favorites.collectAsState()
        val inPlayback = playState.value.current != null
        val searchHistory = vm.searchHistory.collectAsState()
        val toast = vm.toast.collectAsState()

        // 换台记忆：首次切到 IPTV Tab 时恢复上次频道
        var iptvRestored by remember { mutableStateOf(false) }
        LaunchedEffect(lives.value, iptvRestored) {
            if (tabIndex == 2 && !iptvRestored && lives.value.isNotEmpty()) {
                iptvRestored = true
                vm.restoreLastLiveChannel()
            }
        }

        // 全局 toast（2s 后自动消失）
        LaunchedEffect(toast.value) {
            if (toast.value.isNotEmpty()) {
                kotlinx.coroutines.delay(2_000L)
                vm.clearToast()
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
                        if (toast.value.isNotEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            ) {
                                Text(
                                    toast.value,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (inPlayback) {
                            PlaybackScreen(
                                state = playState.value,
                                player = playerCore,
                                danmaku = danmaku.value,
                                epg = epg.value,
                                onSwitch = { line, ep -> vm.switchEpisode(line, ep) },
                                skipIntroMs = skipIntroMs.value,
                                skipOutroMs = skipOutroMs.value,
                                onSkipIntroChange = { vm.setSkipIntro(it) },
                                onSkipOutroChange = { vm.setSkipOutro(it) }
                            )
                        } else {
                            TabRow(selectedTabIndex = tabIndex) {
                                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("点播") })
                                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("短剧") })
                                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("IPTV") })
                                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }, text = { Text("历史") })
                                Tab(selected = tabIndex == 4, onClick = { tabIndex = 4 }, text = { Text("收藏") })
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                loading.value -> LoadingBox()
                                tabIndex == 0 -> VodoTab(
                                    items = results.value,
                                    loading = loading.value,
                                    searchHistory = searchHistory.value,
                                    onSearch = { keyword -> vm.refresh(keyword) },
                                    onHistoryRemove = { q -> vm.removeSearchHistory(q) },
                                    onHistoryClear = { vm.clearSearchHistory() },
                                    onItemClick = { vm.onItemClicked(it) }
                                )
                                tabIndex == 1 -> DramaTab(
                                    episodes = dramas.value,
                                    loading = loadingDramas.value,
                                    onSwitch = { i -> vm.switchDrama(i) },
                                    player = playerCore,
                                    danmaku = danmaku.value
                                )
                                tabIndex == 2 -> IptvTab(
                                    channels = lives.value,
                                    loading = loadingLives.value,
                                    onSelect = { c -> vm.switchLive(c) },
                                    current = playState.value.current,
                                    favorites = liveFavorites.value,
                                    onToggleFavorite = { c -> vm.toggleLiveFavorite(c) }
                                )
                                tabIndex == 3 -> HistoryList(history = playHistory.value, onResume = { id -> vm.resumePlayback(id) })
                                tabIndex == 4 -> FavoritesList(
                                    favorites = favorites.value,
                                    onRemove = { vm.removeFromFavorites(it) },
                                    onClear = { vm.clearFavorites() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 点播 Tab：搜索框 + 历史标签 + 结果列表。 */
@Composable
private fun VodoTab(
    items: List<VideoResult>,
    loading: Boolean,
    searchHistory: List<SearchHistoryEntity>,
    onSearch: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onHistoryClear: () -> Unit,
    onItemClick: (VideoResult) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // 搜索框
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("搜索影视 / 短剧") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
            trailingIcon = {
                IconButton(onClick = {
                    val q = query.ifBlank { "演示" }
                    onSearch(q)
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "搜索")
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = h.query,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.clickable { onSearch(h.query) }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { onHistoryRemove(h.query) }
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
                            .clickable { onHistoryClear() }
                    )
                }
            }

        Spacer(Modifier.height(4.dp))

        // 结果列表
        when {
            loading -> LazyColumn(Modifier.fillMaxSize()) {
                items(8) {
                    SkeletonCard()
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { v ->
                    VideoCard(title = v.title, subtitle = "源: ${v.sourceKey} · ${v.type}") { onItemClick(v) }
                }
                if (items.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无结果，输入关键词或点击右上角刷新")
                        }
                    }
                }
            }
        }
    }
}

/** 短剧 Tab：加载态 / 空态包装，正常时渲染竖屏翻页播放器。 */
@Composable
private fun DramaTab(
    episodes: List<DramaEpisode>,
    loading: Boolean,
    onSwitch: (Int) -> Unit,
    player: PlayerCore,
    danmaku: List<DanmakuItem>
) {
    when {
        loading && episodes.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        episodes.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { Text("暂无短剧") }

        else -> DramaPager(
            episodes = episodes,
            onSwitch = onSwitch,
            player = player,
            danmaku = danmaku
        )
    }
}

/** 短剧竖屏上下滑：VerticalPager 逐集翻页，每页 9:16 竖屏播放器（全屏沉浸）。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DramaPager(
    episodes: List<DramaEpisode>,
    onSwitch: (Int) -> Unit,
    player: PlayerCore,
    danmaku: List<DanmakuItem>
) {
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

/** IPTV Tab：搜索过滤 + 频道列表 + 收藏 + 换台记忆。 */
@Composable
private fun IptvTab(
    channels: List<LiveChannel>,
    loading: Boolean,
    onSelect: (LiveChannel) -> Unit,
    current: PlayMediaItem?,
    favorites: List<com.aggregator.shell.core.data.local.entity.FavoritesEntity>,
    onToggleFavorite: (LiveChannel) -> Unit
) {
    var filter by remember { mutableStateOf("") }
    val filtered = if (filter.isBlank()) channels
    else channels.filter {
        it.name.contains(filter, true) || it.group.contains(filter, true)
    }

    Column(Modifier.fillMaxSize()) {
        if (channels.isNotEmpty()) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("搜索频道 / 分组") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") }
            )
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无直播源") }
        } else {
            IptvList(
                channels = filtered,
                loading = false,
                onSelect = onSelect,
                current = current,
                favorites = favorites,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

/** IPTV 频道列表（含 EPG 节目单 + 频道收藏 + 换台记忆）。 */
@Composable
private fun IptvList(
    channels: List<LiveChannel>,
    loading: Boolean,
    onSelect: (LiveChannel) -> Unit,
    current: PlayMediaItem?,
    favorites: List<com.aggregator.shell.core.data.local.entity.FavoritesEntity>,
    onToggleFavorite: (LiveChannel) -> Unit
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("未找到匹配频道") }
        return
    }
    val isFav = { url: String -> favorites.any { it.contentId == url } }
    LazyColumn(Modifier.fillMaxSize()) {
        items(channels, key = { it.url }) { c ->
            val selected = current?.url == c.url
            val fav = isFav(c.url)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(
                    defaultElevation = if (selected) 2.dp else 0.dp
                )
            ) {
                Row(
                    Modifier
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable { onSelect(c) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = c.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (c.group.isNotBlank()) c.group else "直播",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { onToggleFavorite(c) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (fav) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (fav) "取消收藏" else "收藏频道",
                            tint = if (fav) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
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
    skipIntroMs: Long,
    skipOutroMs: Long,
    onSkipIntroChange: (Long) -> Unit,
    onSkipOutroChange: (Long) -> Unit
) {
    val current = state.current
    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current != null -> {
                Box {
                    PlayerSurface(
                        item = current,
                        player = player,
                        danmaku = danmaku,
                        introEndMs = skipIntroMs,
                        outroStartMs = skipOutroMs
                    )
                    VideoGestureOverlay(player = player)
                }
                if (epg.nowPlaying != null || epg.upcoming.isNotEmpty()) EpgPanel(epg)
                    EpisodeSelector(
                        state = state,
                        onSwitch = onSwitch,
                        skipIntroMs = skipIntroMs,
                        skipOutroMs = skipOutroMs,
                        onSkipIntroChange = onSkipIntroChange,
                        onSkipOutroChange = onSkipOutroChange
                    )
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
private fun EpisodeSelector(
    state: PlayUiState,
    onSwitch: (Int, Int) -> Unit,
    skipIntroMs: Long = 0L,
    skipOutroMs: Long = 0L,
    onSkipIntroChange: (Long) -> Unit = {},
    onSkipOutroChange: (Long) -> Unit = {}
) {
    if (state.episodes.isEmpty()) return

    var introText by remember(skipIntroMs) {
        mutableStateOf((skipIntroMs / 1000L).toString())
    }
    var outroText by remember(skipOutroMs) {
        mutableStateOf((skipOutroMs / 1000L).toString())
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text("线路与集数", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        state.episodes.entries.forEachIndexed { lineIdx, entry ->
            val line = entry.key
            val eps = entry.value
            Text(line, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp)
            ) {
                eps.forEachIndexed { epIdx, _ ->
                    EpisodeChip(
                        label = "${epIdx + 1}",
                        onClick = { onSwitch(lineIdx, epIdx) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("跳过片头/片尾（秒）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("片头结尾:", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = introText,
                onValueChange = { txt ->
                    introText = txt
                    onSkipIntroChange((txt.toLongOrNull() ?: 0L) * 1000L)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .width(72.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(12.dp))
            Text("片尾起点:", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = outroText,
                onValueChange = { txt ->
                    outroText = txt
                    onSkipOutroChange((txt.toLongOrNull() ?: 0L) * 1000L)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(72.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun EpisodeChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .clickable(onClick = onClick)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VideoCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp)) {
            // 左侧色块占位（有封面时替换为 AsyncImage）
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

/** 列表加载态骨架屏卡片（模拟 VideoCard 布局） */
@Composable
private fun SkeletonCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

@Composable
private fun HistoryList(history: List<PlayHistoryEntity>, onResume: (String) -> Unit) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无播放历史")
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(history) { item ->
            HistoryCard(item, onResume = { onResume(item.id) })
        }
    }
}

@Composable
private fun HistoryCard(item: PlayHistoryEntity, onResume: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onResume),
        shape = RoundedCornerShape(14.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "模块: ${item.module} · ${formatDate(item.updated)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (item.positionMs > 0L) {
                Text(
                    "播放进度: ${formatDuration(item.positionMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(date)
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    val remainingSeconds = seconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
    } else {
        String.format("%d:%02d", remainingMinutes, remainingSeconds)
    }
}

@Composable
private fun FavoritesList(
    favorites: List<FavoritesEntity>,
    onRemove: (String) -> Unit,
    onClear: () -> Unit = {}
) {
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无收藏")
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "共 ${favorites.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "清空",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        items(favorites) { item ->
            FavoriteCard(item = item, onRemove = { onRemove(item.id) })
        }
    }
}

@Composable
private fun FavoriteCard(item: FavoritesEntity, onRemove: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "模块: ${item.module} · ${formatDate(item.addedTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (item.category.isNotBlank()) {
                Text(
                    "分类: ${item.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = "取消收藏",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
