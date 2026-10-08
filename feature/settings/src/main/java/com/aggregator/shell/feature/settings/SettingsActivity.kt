package com.aggregator.shell.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.ai.AiSourceAssistant
import com.aggregator.shell.core.common.ModuleType
import com.aggregator.shell.core.data.SubscriptionManager
import com.aggregator.shell.core.data.local.SubscriptionEntity
import com.aggregator.shell.core.ui.components.EmptyState
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Settings shell: manages source subscriptions and the offline AI source
 * assistant. Layout: a top bar with back nav, a module segmented selector,
 * a "add subscription" form, a list of existing subscriptions, and the AI
 * source-generator panel.
 */
@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var assistant: AiSourceAssistant

    @Inject
    lateinit var subscriptions: SubscriptionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { SettingsShell() } }
    }

    @Composable
    private fun SettingsShell() {
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf("") }
        var moduleIndex by remember { mutableIntStateOf(0) }
        var msg by remember { mutableStateOf("") }
        var subs by remember { mutableStateOf<List<SubscriptionEntity>>(emptyList()) }
        var subsLoading by remember { mutableStateOf(false) }

        var sampleUrl by remember { mutableStateOf("") }
        var aiMsg by remember { mutableStateOf("") }
        var aiLoading by remember { mutableStateOf(false) }

        val moduleLabels = listOf("影视", "阅读", "音乐")

        val module = when (moduleIndex) {
            0 -> "video"
            1 -> "reader"
            else -> "music"
        }

        fun loadSubs() {
            subsLoading = true
            scope.launch {
                subs = runCatching { subscriptions.listSubscriptions() }.getOrDefault(emptyList())
                subsLoading = false
            }
        }

        LaunchedEffect(Unit) { loadSubs() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text("接口管理", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "按模块分别管理源订阅。支持在线订阅（拉取 + 去重合并入库）与本地导入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                // Module selector (simple chip-style)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    moduleLabels.forEachIndexed { i, label ->
                        ModuleChip(
                            label = label,
                            selected = moduleIndex == i,
                            onClick = { moduleIndex = i },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Add subscription
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("添加订阅", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("源订阅 URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (url.isBlank()) {
                                msg = "请先填写订阅 URL"
                                return@Button
                            }
                            scope.launch {
                                msg = "正在拉取并去重合并：$url ..."
                                val ok = runCatching {
                                    subscriptions.addSubscription(
                                        "sub-${System.currentTimeMillis()}",
                                        module,
                                        url
                                    )
                                }.isSuccess
                                msg = if (ok) "已订阅：$url（拉取成功，已按 api/url 去重合并入库）"
                                       else "订阅失败，请检查 URL 可达性"
                                loadSubs()
                            }
                        }) {
                            Text("订阅（${moduleLabels[moduleIndex]}）")
                        }
                        if (msg.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Subscription list
                Text("现有订阅", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                when {
                    subsLoading -> Box(
                        Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    subs.isEmpty() -> EmptyState(
                        title = "暂无订阅",
                        hint = "在上方添加一个源订阅 URL",
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )

                    else -> Column(Modifier.fillMaxWidth()) {
                        subs.forEach { sub -> SubCard(sub) {
                            scope.launch {
                                runCatching { subscriptions.remove(sub.subId) }
                                loadSubs()
                            }
                        } }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // AI source assistant
                Text("AI 制源助手", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "默认离线启发式助手。可生成候选源规则、或对失败日志做修复建议。" +
                            "云端 LLM 的 Base URL 与 Key 由您自行提供，应用不读取构建环境中的任何 Key。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = sampleUrl,
                            onValueChange = { sampleUrl = it },
                            label = { Text("目标页 URL（制源）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                aiLoading = true
                                aiMsg = "制源中..."
                                val r = runCatching {
                                    assistant.generateSource(sampleUrl, ModuleType.VIDEO, sampleHtml = null)
                                }.getOrNull()
                                aiMsg = if (r != null) "候选规则：${r.explain}\n配置：${r.config}"
                                       else "制源失败：URL 为空或不可达"
                                aiLoading = false
                            }
                        }) {
                            Text("生成候选源")
                        }
                        if (aiMsg.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(aiMsg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // About
                Text("关于", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "MediaShell v1.1.0 · 壳子 APK · 不内置任何内容源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun ModuleChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    @Composable
    private fun SubCard(sub: SubscriptionEntity, onRemove: () -> Unit) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sub.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${sub.moduleType} · ${sub.url}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
