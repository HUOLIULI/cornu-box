package com.aggregator.shell.feature.video

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.aggregator.shell.core.media.danmaku.DanmakuItem
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.ui.theme.AppTheme
import com.aggregator.shell.feature.video.ui.PlayerSurface
import dagger.hilt.android.AndroidEntryPoint
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
                val playState = vm.play.collectAsState()
                val results = vm.results.collectAsState()
                val loading = vm.loadingResults.collectAsState()
                val dramas = vm.dramas.collectAsState()
                val loadingDramas = vm.loadingDramas.collectAsState()
                val lives = vm.lives.collectAsState()
                val loadingLives = vm.loadingLives.collectAsState()
                val inPlayback = playState.value.current != null

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
                                onSwitch = { line, ep -> vm.switchEpisode(line, ep) }
                            )
                        } else {
                            TabRow(selectedTabIndex = tabIndex) {
                                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("点播") })
                                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("短剧") })
                                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("IPTV") })
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                loading.value -> LoadingBox()
                                tabIndex == 0 -> VodoList(
                                    items = results.value
                                ) { vm.onItemClicked(it) }
                                tabIndex == 1 -> DramaPager(
                                    episodes = dramas.value,
                                    loading = loadingDramas.value,
                                    onSwitch = { i -> vm.switchDrama(i) },
                                    current = playState.value.current
                                )
                                tabIndex == 2 -> IptvList(
                                    channels = lives.value,
                                    loading = loadingLives.value,
                                    onSelect = { c -> vm.switchLive(c) },
                                    current = playState.value.current
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
private fun VodoList(items: List<com.aggregator.shell.core.source.api.VideoResult>, onClick: (com.aggregator.shell.core.source.api.VideoResult) -> Unit) {
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

/** 短剧竖屏上下滑：HorizontalPager 按集翻页，每页 9:16 竖屏区。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DramaPager(
    episodes: List<DramaEpisode>,
    loading: Boolean,
    onSwitch: (Int) -> Unit,
    current: com.aggregator.shell.core.media.player.PlayMediaItem?
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
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val ep = episodes[page]
                VerticalVideo(ep = ep)
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

/** 竖屏 9:16 视频区：复用 PlayerSurface。 */
@Composable
private fun VerticalVideo(ep: DramaEpisode) {
    val current = com.aggregator.shell.core.media.player.PlayMediaItem(
        url = ep.url,
        name = ep.title,
        isHls = ep.isHls
    )
    // 短剧竖屏演示弹幕
    val danmaku = remember(ep.url) {
        listOf(DanmakuItem(0L, "短剧 ${ep.title}"), DanmakuItem(2_000L, "下一集更精彩"))
    }
    // 用 PlayerSurface 需要 player 注入；这里简化为占位 9:16 区域 + 标题，
    // 真实竖屏全屏由 App 层切系统横竖屏控制。
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column {
            Text(ep.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(ep.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** IPTV 列表。 */
@Composable
private fun IptvList(
    channels: List<LiveChannel>,
    loading: Boolean,
    onSelect: (LiveChannel) -> Unit,
    current: com.aggregator.shell.core.media.player.PlayMediaItem?
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
                        Text("播放中", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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
    onSwitch: (Int, Int) -> Unit
) {
    val current = state.current
    val danmaku = remember(current) {
        listOfNotNull(
            DanmakuItem(0L, "演示弹幕 · ${state.detail?.title ?: "媒体"}"),
            DanmakuItem(4_000L, "第一集开始")
        )
    }
    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current != null -> {
                PlayerSurface(item = current, player = player, danmaku = danmaku)
                EpisodeSelector(state = state, onSwitch = onSwitch)
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "未选择媒体")
            }
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
