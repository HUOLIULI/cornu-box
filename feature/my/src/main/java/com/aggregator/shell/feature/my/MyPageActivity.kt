package com.aggregator.shell.feature.my

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aggregator.shell.core.data.local.entity.BookshelfEntity
import com.aggregator.shell.core.data.local.entity.FavoriteEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.ui.components.EmptyState
import com.aggregator.shell.core.ui.theme.AppTheme
import com.aggregator.shell.feature.reader.ReaderActivity
import com.aggregator.shell.feature.music.MusicActivity
import com.aggregator.shell.feature.video.VideoActivity
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "我的"聚合页：书架 / 追剧夹（影视收藏）/ 喜欢（音乐收藏）/ 播放历史 / 搜索历史。
 * 各区块数据走 [MyViewModel]（Room 实时流），点击可跳回对应模块继续看/读/听。
 */
@OptIn(ExperimentalMaterial3Api::class)
class MyPageActivity : ComponentActivity() {

    private val vm: MyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val ui = vm.ui.collectAsState()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("我的") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "返回")
                                }
                            }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                    ) {
                        // 书架
                        item { SectionHeader("书架") }
                        if (ui.value.bookshelf.isEmpty()) {
                            item { SectionEmpty("暂无书籍") }
                        } else {
                            item {
                                LazyRow(Modifier.fillMaxWidth().height(120.dp)) {
                                    items(ui.value.bookshelf) { shelf ->
                                        BookCard(shelf) {
                                            com.aggregator.shell.feature.reader.ReaderActivity.launchBook(
                                                this@MyPageActivity,
                                                com.aggregator.shell.core.source.api.BookResult(
                                                    id = shelf.bookId,
                                                    name = shelf.name,
                                                    author = shelf.author,
                                                    coverUrl = shelf.coverUrl,
                                                    bookUrl = shelf.bookId,
                                                    sourceName = shelf.sourceId
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 追剧夹
                        item { SectionHeader("追剧夹") }
                        if (ui.value.videoFavorites.isEmpty()) {
                            item { SectionEmpty("暂无追剧收藏") }
                        } else {
                            items(ui.value.videoFavorites) { f ->
                                FavoriteRow(f.title, "源: ${f.sourceId}${if (f.subInfo.isNotBlank()) " · ${f.subInfo}" else ""}") {
                                    VideoActivity.openContent(this@MyPageActivity, f)
                                }
                            }
                        }

                        // 喜欢（音乐）
                        item { SectionHeader("我的喜欢") }
                        if (ui.value.musicFavorites.isEmpty()) {
                            item { SectionEmpty("暂无音乐收藏") }
                        } else {
                            items(ui.value.musicFavorites) { f ->
                                FavoriteRow(f.title, f.subInfo) {
                                    MusicActivity.openFavorite(this@MyPageActivity, f)
                                }
                            }
                        }

                        // 播放历史
                        item { SectionHeader("播放历史") }
                        if (ui.value.playHistory.isEmpty()) {
                            item { SectionEmpty("暂无播放记录") }
                        } else {
                            items(ui.value.playHistory) { p ->
                                HistoryRow(p)
                            }
                        }

                        // 搜索历史
                        item { SectionHeader("最近搜索") }
                        if (ui.value.searchHistory.isEmpty()) {
                            item { SectionEmpty("暂无搜索记录") }
                        } else {
                            items(ui.value.searchHistory) { s ->
                                HistoryRow(
                                    com.aggregator.shell.core.data.local.entity.PlayHistoryEntity(
                                        id = s.id, sourceId = s.module, contentId = s.keyword,
                                        title = s.keyword, positionMs = 0, module = s.module, updated = s.ts
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SectionEmpty(hint: String) {
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun BookCard(shelf: BookshelfEntity, onOpen: () -> Unit) {
    Card(
        Modifier
            .width(100.dp)
            .padding(horizontal = 8.dp)
            .clickable { onOpen() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            AsyncImage(
                model = shelf.coverUrl.ifEmpty { null },
                contentDescription = shelf.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(4.dp))
            Text(shelf.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
            Text("${(shelf.readProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FavoriteRow(title: String, subtitle: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Text("打开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HistoryRow(p: PlayHistoryEntity) {
    val timeFmt = rememberDateFmt()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.title.ifBlank { p.contentId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.sourceId} · ${timeFmt.format(Date(p.updated))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun rememberDateFmt() = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
