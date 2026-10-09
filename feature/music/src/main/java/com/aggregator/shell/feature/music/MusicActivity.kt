package com.aggregator.shell.feature.music

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.SkipPrevious
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aggregator.shell.core.data.local.entity.FavoritesEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.source.api.MusicResult
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Music feature shell: a keyword search box, song list with circular cover
 * art, and empty / error / retry states. A user-imported LX Music source
 * (Room) is preferred; the built-in demo is the fallback.
 * Playback is managed by [MusicViewModel] + [MusicNotificationService]
 * (notification bar: play/pause/prev/next).
 */
@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MusicActivity : ComponentActivity() {

    private val model: MusicViewModel by viewModels<MusicViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { MusicShellUi() } }
    }

    @Composable
    private fun MusicShellUi() {
        val context = LocalContext.current
        var tabIndex by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("演示") }
        val songs by model.results.collectAsState()
        val loading by model.loadingResults.collectAsState()
        val playState by model.play.collectAsState()
        val playHistory by model.playHistory.collectAsState()
        val favorites by model.favorites.collectAsState()
        val searchHistory by model.searchHistory.collectAsState()

        fun runSearch() {
            model.refresh(query.ifBlank { "演示" })
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("音乐") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (tabIndex == 0) {
                            IconButton(onClick = { runSearch() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (tabIndex == 0) {
                    androidx.compose.material3.FloatingActionButton(onClick = { /* source editor (see Settings) */ }) {
                        Icon(Icons.Default.Search, contentDescription = "添加音乐源")
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("搜索") },
                        icon = { Icon(Icons.Default.Search, contentDescription = "搜索") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("历史") },
                        icon = { Icon(Icons.Default.History, contentDescription = "历史") }
                    )
                    Tab(
                        selected = tabIndex == 2,
                        onClick = { tabIndex = 2 },
                        text = { Text("收藏") },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "收藏") }
                    )
                }

                // 播放控制条：当前歌曲 + 播放/暂停/上一曲/下一曲
                if (playState.current != null) {
                    MusicPlayerBar(
                        song = playState.current,
                        playing = playState.playing,
                        onPlayPause = {
                            if (playState.playing) model.pause(context) else model.resume(context)
                        },
                        onPrev = { model.prevTrack(context) },
                        onNext = { model.nextTrack(context) },
                        onStop = { model.stop() }
                    )
                }

                when (tabIndex) {
                    0 -> {
                        // Search Tab
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            placeholder = { Text("搜索歌曲 / 歌手") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                            trailingIcon = {
                                IconButton(onClick = { runSearch() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "搜索")
                                }
                            }
                        )

                        // 搜索历史标签
                        if (searchHistory.value.isNotEmpty()) {
                            Row(
                                Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                searchHistory.value.forEach { h ->
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = h.query,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.clickable {
                                                    query = h.query
                                                    runSearch()
                                                }
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "×",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier.clickable { model.removeSearchHistory(h.query) }
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
                                        .clickable { model.clearSearchHistory() }
                                )
                            }
                        }

                        when {
                            loading -> Box(
                                Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }

                            else -> LazyColumn(Modifier.fillMaxSize()) {
                                items(songs) { SongCard(it, model) }
                            }
                        }
                    }
                    1 -> {
                        // History Tab
                        when {
                            playHistory.isEmpty() -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无播放历史")
                            }

                            else -> LazyColumn(Modifier.fillMaxSize()) {
                                items(playHistory) { item ->
                                    HistoryCard(item)
                                }
                            }
                        }
                    }
                    2 -> {
                        // Favorites Tab
                        when {
                            favorites.isEmpty() -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无收藏")
                            }

                            else -> LazyColumn(Modifier.fillMaxSize()) {
                                items(favorites) { item ->
                                    FavoriteCard(item, model)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MusicPlayerBar(
        song: MusicResult,
        playing: Boolean,
        onPlayPause: () -> Unit,
        onPrev: () -> Unit,
        onNext: () -> Unit,
        onStop: () -> Unit
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.picUrl.ifEmpty { null },
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${song.artist} - ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.SkipPrevious, contentDescription = "上一曲")
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放"
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.SkipNext, contentDescription = "下一曲")
                }
            }
        }
    }

    @Composable
    private fun SongCard(s: MusicResult, model: MusicViewModel) {
        val context = LocalContext.current
        val isCurrent = model.play.value.current?.id == s.id
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 6.dp else 2.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
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
                    Text(
                        s.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${s.artist} - ${s.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                  IconButton(onClick = {
                    model.play(s, context)
                  }) {
                    Icon(
                        if (isCurrent && model.play.value.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isCurrent) "暂停" else "播放",
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    @Composable
    private fun HistoryCard(item: com.aggregator.shell.core.data.local.entity.PlayHistoryEntity) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
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

    @Composable
    private fun FavoriteCard(item: com.aggregator.shell.core.data.local.entity.FavoritesEntity, model: MusicViewModel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
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
                IconButton(onClick = { model.removeFromFavorites(item.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "取消收藏", tint = MaterialTheme.colorScheme.error)
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
}
