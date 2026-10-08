package com.aggregator.shell.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aggregator.shell.core.ai.AiSourceAssistant
import com.aggregator.shell.core.common.ModuleType
import com.aggregator.shell.core.data.SubscriptionManager
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
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
        var module by remember { mutableStateOf("video") }
        var msg by remember { mutableStateOf("") }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("接口管理", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("源订阅 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (url.isBlank()) { msg = "请先填写订阅 URL"; return@Button }
                    scope.launch {
                        msg = "正在拉取并去重合并：$url ..."
                        val ok = runCatching {
                            subscriptions.addSubscription("sub-${System.currentTimeMillis()}", module, url)
                        }.isSuccess
                        msg = if (ok) "已订阅：$url（拉取成功，已按 api/url 去重合并入库）"
                              else "订阅失败，请检查 URL 可达性"
                    }
                }) { Text("订阅（$module）") }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Text("AI 制源助手", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "默认离线启发式助手。下方可生成候选源规则、或对失败日志做修复建议。" +
                        "云端 LLM 的 Base URL 与 Key 由您自行提供并写入设置，应用不读取构建环境中的任何 Key。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            var sampleUrl by remember { mutableStateOf("") }
            var aiMsg by remember { mutableStateOf("") }
            OutlinedTextField(
                value = sampleUrl,
                onValueChange = { sampleUrl = it },
                label = { Text("目标页 URL（制源）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        aiMsg = "制源中..."
                        val r = runCatching {
                            assistant.generateSource(sampleUrl, ModuleType.VIDEO, sampleHtml = null)
                        }.getOrNull()
                        aiMsg = if (r != null) "候选规则：${r.explain}\n配置：${r.config}"
                              else "制源失败：URL 为空或不可达"
                    }
                }) { Text("生成候选源") }
            }
            if (aiMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(aiMsg, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Text("关于", style = MaterialTheme.typography.titleLarge)
            Text("MediaShell v1.0.0 · 壳子 APK · 不内置任何内容源", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
