package com.aggregator.shell.feature.music

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aggregator.shell.core.media.player.PlayerCore
import com.aggregator.shell.core.media.lyric.LrcParser
import com.aggregator.shell.core.source.api.MusicResult
import com.aggregator.shell.core.ui.components.EmptyState
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 音乐 feature：搜索（含最近搜索）+ 歌曲列表 + 播放页（接 [PlayerCore] 真实播放）
 * + LRC 歌词逐行同步 + 播放队列 + 收藏 + 断点续播。
 */
@OptIn(ExperimentalMaterial3Api::class)
class MusicActivity : ComponentActivity() {

    @Inject
    lateinit var playerCore: PlayerCore

    private val vm: MusicViewModel by viewModels()

    companion object {
        /** 从「我的」喜欢区拉起该歌曲进入播放。 */
        fun openFavorite(ctx: android.content.Context, favorite: com.aggregator.shell.core.data.local.entity.FavoriteEntity) {
            ctx.startActivity(
                android.content.Intent(ctx, MusicActivity::class.java)
                    .putExtra("extra_song_id", favorite.contentId)
                    .putExtra("extra_song_title", favorite.title)
                    .putExtra("extra_song_artist", favorite.subInfo)
                    .putExtra("extra_song_source", favorite.sourceId)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerCore.initialize(this)
        val extraSongId = intent.getStringExtra("extra_song_id")
        val extraSongTitle = intent.getStringExtra("extra_song_title")
        val extraSongArtist = intent.getStringExtra("extra_song_artist")
        val extraSongSource = intent.getStringExtra("extra_song_source")
        setContent {
            AppTheme {
                var query by remember { mutableStateOf("演示") }
                var showPlayer by remember { mutableStateOf(false) }

                val songs = vm.songs.collectAsState()
                val searchHistory = vm.searchHistory.collectAsState()
                val loading = vm.loading.collectAsState()
                val error = vm.error.collectAsState()
                val player = vm.player.collectAsState()
                val favorites = vm.favorites.collectAsState()

                var isPlaying by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (extraSongId != null) {
                        val song = com.aggregator.shell.core.source.api.MusicResult(
                            id = extraSongId,
                            title = extraSongTitle.orEmpty(),
                            artist = extraSongArtist.orEmpty(),
                            album = "",
                            source = extraSongSource.orEmpty()
                        )
                        showPlayer = true
                        vm.playSong(song, listOf(song))
                        isPlaying = true
                    } else {
                        vm.search(query.ifBlank { "演示" })
                    }
                }

                androidx.compose.runtime.LaunchedEffect(showPlayer) {
                    if (showPlayer && isPlaying) {
                        playerCore.startService(this@MusicActivity)
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (showPlayer) "正在播放" else "音乐") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (showPlayer) showPlayer = false else finish()
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (showPlayer && player.value.current != null) {
                            PlayerScreen(
                                ui = player.value,
                                player = playerCore,
                                isFavorited = songInFav(player.value, favorites.value),
                                onPlayPause = { vm.togglePlayPause() },
                                onNext = { vm.playNext() },
                                onPrev = { vm.playPrev() },
                                onSeek = { ms -> vm.seekTo(ms) },
                                onFavorite = { song -> vm.toggleFavorite(song) },
                                onStop = {
                                    vm.stopPlayback()
                                    showPlayer = false
                                },
                                onDesktopLyrics = { vm.toggleDesktopLyrics() },
                                desktopLyricsOn = vm.desktopLyrics.collectAsState().value
                            )
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("搜索歌曲 / 歌手") },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                                        trailingIcon = {
                                            IconButton(onClick = { vm.search(query) }) {
                                                Icon(Icons.Default.Search, contentDescription = "搜索")
                                            }
                                        }
                                    )
                                }
                                val chips = searchHistory.value.filter { it.module == "MUSIC" && it.keyword.isNotBlank() }.take(8)
                                if (chips.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    LazyRow(
                                        Modifier.fillMaxWidth().height(44.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(chips) { h ->
                                            AssistChip(onClick = { vm.search(h.keyword) }, label = { Text(h.keyword) })
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                                when {
                                    loading.value -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                    error.value != null -> EmptyState(
                                        title = "加载失败",
                                        hint = error.value ?: "搜索出错了",
                                        actionLabel = "重试",
                                        onAction = { vm.search(query) }
                                    )
                                    songs.value.isEmpty() -> EmptyState(
                                        title = "暂无歌曲",
                                        hint = "换个关键词或在设置中导入音乐源",
                                        actionLabel = "重新搜索",
                                        onAction = { vm.search(query) }
                                    )
                                    else -> LazyColumn(Modifier.fillMaxSize()) {
                                        items(songs.value) { s ->
                                            SongCard(
                                                s,
                                                isPlaying = player.value.current?.name == s.title && player.value.isPlaying
                                            ) {
                                                showPlayer = true
                                                vm.playSong(s, songs.value)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 播放页：封面 + 控件 + 进度条 + LRC 歌词逐行同步 + 队列。 */
@Composable
private fun PlayerScreen(
    ui: MusicPlayerUi,
    player: PlayerCore,
    isFavorited: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavorite: (MusicResult) -> Unit,
    onStop: () -> Unit,
    onDesktopLyrics: () -> Unit = {},
    desktopLyricsOn: Boolean = false
) {
    val current = ui.current ?: return
    val song = ui.queue.getOrNull(ui.queueIndex)
    val activeLine = LrcParser.lineAt(ui.lyric, ui.positionMs)

    Column(Modifier.fillMaxSize()) {
        // 封面 + 标题
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = song?.picUrl?.ifEmpty { null },
                    contentDescription = song?.title,
                    modifier = Modifier
                        .size(160.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    song?.title ?: current.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    song?.let { "${it.artist} - ${it.album}" } ?: current.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // 进度条
        val duration = ui.durationMs.takeIf { it > 0L } ?: (ui.lyric.durationMs.takeIf { it > 0L } ?: 1L)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(ui.positionMs), style = MaterialTheme.typography.labelSmall)
            Slider(
                value = (ui.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac -> onSeek((frac * duration).toLong()) },
                modifier = Modifier.weight(1f)
            )
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
        }

        // 控制按钮
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.weight(1f)) {
                Icon(
                    if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (ui.isPlaying) "暂停" else "播放"
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }
        }

        // 歌词（逐行高亮）
        if (ui.lyric.lines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(ui.lyric.lines.size) { i ->
                    val line = ui.lyric.lines[i]
                    Text(
                        text = line.text,
                        style = if (i == activeLine) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        color = if (i == activeLine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // 队列 + 桌面歌词 + 操作
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "队列 ${ui.queueIndex + 1}/${ui.queue.size}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            if (song != null) {
                IconButton(onClick = { onFavorite(song) }) {
                    Icon(
                        if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏"
                    )
                }
            }
            AssistChip(
                onClick = onDesktopLyrics,
                label = { Text(if (desktopLyricsOn) "桌面歌词：开" else "桌面歌词：关") }
            )
            TextButtonPlaceholder(onStop)
        }
    }
}

@Composable
private fun TextButtonPlaceholder(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text("停止", color = MaterialTheme.colorScheme.primary)
    }
}

private fun songInFav(ui: MusicPlayerUi, favs: List<com.aggregator.shell.core.data.local.entity.FavoriteEntity>): Boolean {
    val song = ui.queue.getOrNull(ui.queueIndex) ?: return false
    return favs.any { it.contentId == song.id && it.module == "music" }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/** 歌曲卡片。 */
@Composable
private fun SongCard(s: MusicResult, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth()) {
            AsyncImage(
                model = s.picUrl.ifEmpty { null },
                contentDescription = s.title,
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${s.artist} - ${s.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.MusicNote,
                contentDescription = if (isPlaying) "播放中" else "播放",
                tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        }
    }
}
