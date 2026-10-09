package com.aggregator.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
        Column(Modifier.padding(16.dp)) {
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
