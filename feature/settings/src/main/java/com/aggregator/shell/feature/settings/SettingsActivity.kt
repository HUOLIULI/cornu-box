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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aggregator.shell.core.ai.AiSourceAssistant
import com.aggregator.shell.core.common.ModuleType
import com.aggregator.shell.core.data.BackupManager
import com.aggregator.shell.core.data.SubscriptionManager
import com.aggregator.shell.core.data.di.appDataStore
import com.aggregator.shell.core.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var assistant: AiSourceAssistant

    @Inject
    lateinit var subscriptionManager: SubscriptionManager

    @Inject
    lateinit var backupManager: BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { SettingsShell() } }
    }

    @Composable
    private fun SettingsShell() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf("") }
        var module by remember { mutableStateOf(ModuleType.VIDEO) }
        var msg by remember { mutableStateOf("") }
        var importing by remember { mutableStateOf(false) }
        var darkMode by remember { mutableStateOf(false) }
        var backupVersion by remember { mutableIntStateOf(0) }
        var backupFiles by remember { mutableStateOf(backupManager.listBackups()) }
        var pendingRestoreFile by remember { mutableStateOf<java.io.File?>(null) }
        var showRestoreConfirm by remember { mutableStateOf(false) }

        // LLM 配置：从 DataStore 回填
        var baseUrl by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }
        // 弹幕源配置：从 DataStore 回填
        var danmakuUrl by remember { mutableStateOf("") }
        var danmakuKey by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val prefs = context.appDataStore.data.first()
            baseUrl = prefs[KEY_BASE_URL] ?: ""
            apiKey = prefs[KEY_API_KEY] ?: ""
            danmakuUrl = prefs[KEY_DANMAKU_BASE_URL] ?: ""
            danmakuKey = prefs[KEY_DANMAKU_API_KEY] ?: ""
            darkMode = prefs[KEY_DARK_MODE] ?: false
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("接口管理", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("源订阅 URL（影视 JSON / 书源 JSON / 音乐脚本）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ModuleType.VIDEO to "影视",
                    ModuleType.READER to "阅读",
                    ModuleType.MUSIC to "音乐"
                ).forEach { (m, label) ->
                    FilterChip(
                        selected = module == m,
                        onClick = { module = m },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = url.isNotBlank() && !importing,
                    onClick = {
                        importing = true
                        scope.launch {
                            runCatching {
                                subscriptionManager.addSubscription(
                                    name = url.substringAfterLast('/').ifBlank { url },
                                    module = module.name,
                                    url = url
                                )
                            }.onSuccess {
                                msg = "导入成功：$url（已去重合并）"
                            }.onFailure { e ->
                                msg = "导入失败：${e.message}"
                            }
                            importing = false
                        }
                    }
                ) { Text(if (importing) "导入中…" else "导入/订阅") }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Text("AI 制源助手", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "默认使用离线启发式助手（HeuristicAssistant）。" +
                        "若要启用云端 LLM，请在下方填入 Base URL 与 API Key（由您自行提供，" +
                        "应用不会读取构建环境中的任何 Key）。配置仅保存在本机 DataStore。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("LLM Base URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { v -> apiKey = v },
                label = { Text("LLM API Key（仅保存在本机）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        context.appDataStore.edit { p ->
                            p[KEY_BASE_URL] = baseUrl.trim()
                            p[KEY_API_KEY] = apiKey.trim()
                        }
                        msg = "LLM 配置已保存"
                    }
                }) { Text("保存 LLM 配置") }
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            assistant.generateSource(
                                targetUrl = url.ifBlank { "https://example.com" },
                                module = module,
                                sampleHtml = null
                            )
                        }.onSuccess { r ->
                            msg = "制源建议：${r.explain}"
                        }.onFailure { e ->
                            msg = "制源失败：${e.message}"
                        }
                    }
                }) { Text("测试制源") }
            }

            Spacer(Modifier.height(16.dp))
            Text("弹幕源", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "配置真实弹幕 API（如 DanDanPlay 风格）。仅保存在本机 DataStore，" +
                        "应用不内置/不读取任何平台凭据；留空则使用内置演示弹幕。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = danmakuUrl, onValueChange = { danmakuUrl = it },
                label = { Text("弹幕 API Base URL（可选）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = danmakuKey,
                onValueChange = { danmakuKey = it },
                label = { Text("弹幕 API Key（可选，仅保存在本机）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        context.appDataStore.edit { p ->
                            p[KEY_DANMAKU_BASE_URL] = danmakuUrl.trim()
                            p[KEY_DANMAKU_API_KEY] = danmakuKey.trim()
                        }
                        msg = "弹幕源配置已保存"
                    }
                }) { Text("保存弹幕源") }
            }

            Spacer(Modifier.height(16.dp))
            Text("备份与恢复", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "导出全量备份（数据库 + 配置）到本地文件，换机后可导入恢复。" +
                        "恢复会清空当前数据并覆盖为备份内容。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            val file = backupManager.exportToLocalFile()
                            msg = "备份已导出：${file.name}（${file.length()} 字节）"
                            backupVersion++
                            backupFiles = backupManager.listBackups()
                        }.onFailure { e ->
                            msg = "备份失败：${e.message}"
                        }
                    }
                }) { Text("导出备份") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "可用备份文件（${backupFiles.size}）：",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            backupFiles.take(5).forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${file.name} · ${file.length() / 1024}KB",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            pendingRestoreFile = file
                            showRestoreConfirm = true
                        },
                        enabled = !importing
                    ) { Text("恢复") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val deleted = backupManager.deleteBackup(file)
                            if (deleted) {
                                backupFiles = backupFiles.filter { it != file }
                                msg = "已删除 ${file.name}"
                            }
                        },
                        enabled = !importing
                    ) { Text("删除") }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("外观设置", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (darkMode) Icons.Default.Brightness4 else Icons.Default.BrightnessHigh,
                        contentDescription = "主题图标",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = darkMode,
                    onCheckedChange = { checked ->
                        darkMode = checked
                        scope.launch {
                            context.appDataStore.edit { prefs ->
                                prefs[KEY_DARK_MODE] = checked
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("关于", style = MaterialTheme.typography.titleLarge)
            Text("MediaShell v1.5.1 · 壳子 APK · 不内置任何内容源", style = MaterialTheme.typography.bodyMedium)
        }

        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text("确认恢复备份") },
                text = {
                    Text(
                        "恢复将清空当前所有数据（影视源、书源、收藏、播放历史、配置）" +
                                "并替换为 ${pendingRestoreFile?.name ?: "备份文件"} 的内容，操作不可撤销。"
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val file = pendingRestoreFile
                        showRestoreConfirm = false
                        if (file != null) {
                            scope.launch {
                                runCatching {
                                    backupManager.restoreFromLocalFile(file)
                                    msg = "已从 ${file.name} 恢复"
                                    backupVersion++
                                }.onFailure { e ->
                                    msg = "恢复失败：${e.message}"
                                }
                            }
                        }
                    }) { Text("确认恢复") }
                },
                dismissButton = {
                    Button(onClick = {
                        pendingRestoreFile = null
                        showRestoreConfirm = false
                    }) { Text("取消") }
                }
            )
        }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("llm_base_url")
        private val KEY_API_KEY = stringPreferencesKey("llm_api_key")
        private val KEY_DANMAKU_BASE_URL = stringPreferencesKey("danmaku_base_url")
        private val KEY_DANMAKU_API_KEY = stringPreferencesKey("danmaku_api_key")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
    }
}
