package com.aggregator.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aggregator.shell.core.ui.theme.AppTheme
import com.aggregator.shell.feature.music.MusicActivity
import com.aggregator.shell.feature.my.MyPageActivity
import com.aggregator.shell.feature.reader.ReaderActivity
import com.aggregator.shell.feature.settings.SettingsActivity
import com.aggregator.shell.feature.video.VideoActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

        Scaffold(
            bottomBar = {
                NavigationBar {
                    navBarEntry(route = "video", icon = Icons.Filled.Movie, label = "影视", nav = nav, current = current)
                    navBarEntry(route = "reader", icon = Icons.AutoMirrored.Filled.MenuBook, label = "阅读", nav = nav, current = current)
                    navBarEntry(route = "music", icon = Icons.Filled.MusicNote, label = "音乐", nav = nav, current = current)
                    navBarEntry(route = "my", icon = Icons.Filled.Person, label = "我的", nav = nav, current = current)
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
                composable("video") { BridgePanel("影视", VideoActivity::class.java) }
                composable("reader") { BridgePanel("阅读", ReaderActivity::class.java) }
                composable("music") { BridgePanel("音乐", MusicActivity::class.java) }
                composable("my") { BridgePanel("我的", MyPageActivity::class.java) }
                composable("settings") { BridgePanel("设置", SettingsActivity::class.java) }
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
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ModuleHero(title, target, context)
            Spacer(Modifier.height(8.dp))
            ModuleCapabilityGrid(target, context)
        }
    }

    /** 顶部入口 Hero：标题 + 模块简介 + 进入按钮，替代原先的空壳 Card。 */
    @Composable
    private fun ModuleHero(
        title: String,
        target: Class<out ComponentActivity>,
        context: android.content.Context
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = moduleHint(target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { context.startActivity(Intent(context, target)) },
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("进入$title")
                }
            }
        }
    }

    /** 模块能力芯片网格：把 3 个对标 APK 吸纳的能力以 chips 形式直观呈现。 */
    @Composable
    private fun ModuleCapabilityGrid(
        target: Class<out ComponentActivity>,
        context: android.content.Context
    ) {
        Text("本模块能力", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val caps = moduleCapabilities(target)
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(caps) { cap ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(cap.first, maxLines = 1) },
                            modifier = Modifier.widthIn(min = 72.dp, max = 96.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            cap.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    private fun moduleHint(target: Class<out ComponentActivity>): String = when (target) {
        VideoActivity::class.java -> "影视点播 · 短剧竖滑 · IPTV 直播 + EPG · 弹幕"
        ReaderActivity::class.java -> "书源搜索 · 书架进度 · 单文件书源脚本 · TTS 朗读"
        MusicActivity::class.java -> "音乐搜索 · 播放队列 · LRC 同步 · 桌面歌词浮层"
        MyPageActivity::class.java -> "追剧夹 · 我的喜欢 · 播放/搜索历史 · 书架"
        SettingsActivity::class.java -> "源订阅 · LLM 制源 · 弹幕源 · TTS 听书"
        else -> "模块已接入"
    }

    private fun moduleCapabilities(target: Class<out ComponentActivity>): List<Pair<String, String>> =
        when (target) {
            VideoActivity::class.java -> listOf(
                "点播" to "影视源聚合 + 分集播放 + 追剧夹",
                "短剧" to "VerticalPager 竖滑 9:16 全屏",
                "IPTV" to "直播列表 + XMLTV EPG 节目单",
                "弹幕" to "配置化远程弹幕源 / 演示兜底",
                "后台" to "mediaPlayback 前台服务 + 通知"
            )
            ReaderActivity::class.java -> listOf(
                "书源" to "Legado 规则 + mainJs 单文件脚本源",
                "书架" to "进度回写 / 继续读 / 移除",
                "搜索" to "并发聚合 + 历史 chips",
                "TTS" to "章节送云端/离线 TTS 朗读",
                "断点" to "阅读位置持久化"
            )
            MusicActivity::class.java -> listOf(
                "播放" to "队列 + 上/下一首 + 断点续播",
                "歌词" to "LRC 逐行高亮同步",
                "桌面歌词" to "浮层歌词（WindowManager 悬浮）",
                "收藏" to "我的喜欢 / 歌单",
                "历史" to "最近搜索 chips"
            )
            MyPageActivity::class.java -> listOf(
                "追剧夹" to "影视收藏 + 断点定位",
                "喜欢" to "音乐 / 书签收藏",
                "历史" to "播放 + 搜索记录聚合",
                "书架" to "阅读进度一览",
                "跳转" to "点击直达对应模块播放/阅读"
            )
            SettingsActivity::class.java -> listOf(
                "源订阅" to "影视 / 书源 / 音乐脚本导入",
                "LLM" to "可配置云端制源（OpenAI 兼容）",
                "弹幕源" to "真实弹幕 API 端点",
                "TTS" to "听书朗读端点 / 音色",
                "安全" to "端点与 Key 仅存本机 DataStore"
            )
            else -> emptyList()
        }
}
