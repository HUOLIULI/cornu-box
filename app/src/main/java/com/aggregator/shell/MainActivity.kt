package com.aggregator.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aggregator.shell.core.data.local.BookshelfDao
import com.aggregator.shell.core.data.local.PlayHistoryDao
import com.aggregator.shell.core.data.local.entity.BookshelfEntity
import com.aggregator.shell.core.data.local.entity.PlayHistoryEntity
import com.aggregator.shell.core.ui.theme.AppTheme
import com.aggregator.shell.feature.music.MusicActivity
import com.aggregator.shell.feature.reader.ReaderActivity
import com.aggregator.shell.feature.settings.SettingsActivity
import com.aggregator.shell.feature.video.VideoActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playHistoryDao: PlayHistoryDao

    @Inject
    lateinit var bookshelfDao: BookshelfDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppShell()
            }
        }
    }

    @Composable
    private fun AppShell() {
        val nav = rememberNavController()
        val current = nav.currentBackStackEntryAsState().value?.destination?.route ?: "video"
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var latestVideo by remember {
            mutableStateOf<PlayHistoryEntity?>(null)
        }
        var latestBook by remember {
            mutableStateOf<BookshelfEntity?>(null)
        }

        LaunchedEffect(Unit) {
            playHistoryDao.byModule("video").first().firstOrNull()?.let { latestVideo = it }
            bookshelfDao.all().first().firstOrNull()?.let { latestBook = it }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    navBarEntry(route = "video", icon = Icons.Filled.Movie, label = "影视", nav = nav, current = current)
                    navBarEntry(route = "reader", icon = Icons.Filled.MenuBook, label = "阅读", nav = nav, current = current)
                    navBarEntry(route = "music", icon = Icons.Filled.MusicNote, label = "音乐", nav = nav, current = current)
                    navBarEntry(route = "settings", icon = Icons.Filled.Settings, label = "设置", nav = nav, current = current)
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = nav,
                startDestination = "video",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable("video") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        if (latestVideo != null) {
                            QuickEntryCard(
                                title = "继续播放",
                                subtitle = latestVideo?.title ?: "",
                                icon = Icons.Filled.PlayArrow,
                                onClick = { context.startActivity(Intent(context, VideoActivity::class.java)) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        BridgePanel("影视", VideoActivity::class.java)
                    }
                }
                composable("reader") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        if (latestBook != null) {
                            QuickEntryCard(
                                title = "继续阅读",
                                subtitle = latestBook?.name ?: "",
                                icon = Icons.Filled.MenuBook,
                                onClick = { context.startActivity(Intent(context, ReaderActivity::class.java)) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        BridgePanel("阅读", ReaderActivity::class.java)
                    }
                }
                composable("music") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        BridgePanel("音乐", MusicActivity::class.java)
                    }
                }
                composable("settings") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        BridgePanel("设置", SettingsActivity::class.java)
                    }
                }
            }
        }
    }

    @Composable
    private fun QuickEntryCard(
        title: String,
        subtitle: String,
        icon: ImageVector,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onClick, shape = RoundedCornerShape(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("打开", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    @Composable
    private fun RowScope.navBarEntry(
        route: String,
        icon: ImageVector,
        label: String,
        nav: NavHostController,
        current: String
    ) {
        NavigationBarItem(
            selected = current == route,
            onClick = {
                nav.navigate(route) {
                    launchSingleTop = true
                    popUpTo(0) { saveState = true }
                }
            },
            icon = { Icon(imageVector = icon, contentDescription = label) },
            label = { Text(label) }
        )
    }

    @Composable
    private fun BridgePanel(
        title: String,
        target: Class<out ComponentActivity>
    ) {
        val context = LocalContext.current
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "模块已接入。点击按钮进入完整体验。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    context.startActivity(Intent(context, target))
                }) { Text("进入$title") }
            }
        }
    }
}
