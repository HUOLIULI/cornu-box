package com.aggregator.shell.core.ai

import android.content.Context
import com.aggregator.shell.core.ai.di.LlmClient
import com.aggregator.shell.core.common.TtsConfigKeys
import com.aggregator.shell.core.data.di.appDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

/**
 * 文本转语音（听书朗读）助手。
 *
 * 云端实现 [CloudTtsAssistant] 走 OpenAI 兼容 `audio/speech` 端点，返回可播放
 * 的本地 MP3 路径；离线实现 [OfflineTtsAssistant] 返回 null。端点与 Key 仅存
 * 本机 DataStore（[TtsConfigKeys]），不读构建环境。
 */
interface TtsAssistant {
    /** 合成文本；返回可播放的本地 MP3 路径（file:// URL），未配置 / 失败返回 null。 */
    suspend fun synthesize(text: String): String?
}

/**
 * OpenAI 兼容 TTS 端点。读取 [TtsConfigKeys]（本机 DataStore），POST
 * `/audio/speech`（`input` / `model` / `voice`），把返回的二进制流写入应用缓存目录，
 * 返回 `file://` 路径供 [com.aggregator.shell.core.media.player.PlayerCore] 播放。
 */
class CloudTtsAssistant @Inject constructor(
    @LlmClient private val client: OkHttpClient,
    @ApplicationContext private val context: Context
) : TtsAssistant {

    private companion object {
        const val DEFAULT_MODEL = "tts-1"
        const val DEFAULT_VOICE = "alloy"
        const val MAX_CHARS = 5000
        const val CACHE_DIR = "tts_cache"
        const val MAX_CACHE_FILES = 64
    }

    override suspend fun synthesize(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        val prefs = context.appDataStore.data.first()
        val baseUrl = (prefs[TtsConfigKeys.BASE_URL]?.trim()?.takeIf { it.isNotBlank() } ?: "").removeSuffix("/audio/speech")
        val apiKey = prefs[TtsConfigKeys.API_KEY]?.trim()?.takeIf { it.isNotBlank() }
        if (baseUrl.isEmpty() || apiKey == null) return@withContext null
        val voice = prefs[TtsConfigKeys.VOICE]?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_VOICE
        val model = prefs[TtsConfigKeys.MODEL]?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        val cacheDir = java.io.File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val cacheKey = cacheKeyOf("$model|$voice|${text.take(MAX_CHARS)}")
        val cached = java.io.File(cacheDir, "$cacheKey.mp3")
        if (cached.exists() && cached.length() > 0) {
            cached.setLastModified(System.currentTimeMillis())
            return@withContext "file://${cached.absolutePath}"
        }

        val body = JSONObject().apply {
            put("model", model)
            put("voice", voice)
            put("input", text.take(MAX_CHARS))
        }

        val request = Request.Builder()
            .url("$baseUrl/audio/speech")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val bytes = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            r.body?.bytes() ?: return@withContext null
        }
        if (bytes.isEmpty()) return@withContext null

        val tmp = java.io.File(cacheDir, "$cacheKey.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(cached)) {
            tmp.copyTo(cached, overwrite = true)
            tmp.delete()
        }
        trimCache(cacheDir)
        "file://${cached.absolutePath}"
    }

    private fun cacheKeyOf(input: String): String =
        java.security.MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun trimCache(dir: java.io.File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".mp3") } ?: return
        if (files.size <= MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHE_FILES)
            .forEach { runCatching { it.delete() } }
    }
}

/** 离线兜底：未配置云端 TTS 时返回 null，由调用方提示引导配置。 */
class OfflineTtsAssistant @Inject constructor() : TtsAssistant {
    override suspend fun synthesize(text: String): String? = null
}

/**
 * 按本机 DataStore 配置动态切换云端 / 离线 TTS，未配置时透明回退离线。
 */
class TtsDelegatingAssistant(
    @ApplicationContext private val context: Context,
    private val cloud: CloudTtsAssistant,
    private val offline: OfflineTtsAssistant
) : TtsAssistant {

    private suspend fun useCloud(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.appDataStore.data.first()
            (prefs[TtsConfigKeys.BASE_URL]?.trim()?.isNotBlank() == true)
                && (prefs[TtsConfigKeys.API_KEY]?.trim()?.isNotBlank() == true)
        }.getOrDefault(false)
    }

    override suspend fun synthesize(text: String): String? =
        (if (useCloud()) cloud else offline).synthesize(text)
}
