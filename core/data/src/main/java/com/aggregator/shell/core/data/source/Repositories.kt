package com.aggregator.shell.core.data.source

import android.content.Context
import com.aggregator.shell.core.common.AppException
import com.aggregator.shell.core.data.MusicSourceRepo
import com.aggregator.shell.core.data.ReaderSourceRepo
import com.aggregator.shell.core.data.SubscriptionManager
import com.aggregator.shell.core.data.VideoSourceRepo
import com.aggregator.shell.core.data.local.BookSourceDao
import com.aggregator.shell.core.data.local.LiveSourceDao
import com.aggregator.shell.core.data.local.MusicSourceDao
import com.aggregator.shell.core.data.local.SubscriptionDao
import com.aggregator.shell.core.data.local.VideoSourceDao
import com.aggregator.shell.core.data.local.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID

class VideoSourceRepoImpl @javax.inject.Inject constructor(
    private val videoDao: VideoSourceDao,
    private val liveDao: LiveSourceDao,
    @ApplicationContext private val context: Context
) : VideoSourceRepo {

    private val client = OkHttpClient.Builder().build()

    override suspend fun upsertLocal(name: String, configJson: String) {
        val parsed = try {
            org.json.JSONObject(configJson)
        } catch (e: Exception) {
            throw AppException.RuleParseException(configJson.take(32), "JSON 格式无效")
        }
        val sites = parsed.optJSONArray("sites")
        (0 until (sites?.length() ?: 0)).forEach { i ->
            val site = sites!!.getJSONObject(i)
            // 以 key/api 为幂等主键，重复导入自动覆盖去重
            val key = site.optString("key").ifBlank { site.optString("api") }
            videoDao.upsert(
                VideoSourceEntity(
                    sourceId = "tvb-$key",
                    name = site.optString("name", "未命名源 $i"),
                    api = site.optString("api", ""),
                    spider = site.optString("spider", ""),
                    ext = site.optString("ext", ""),
                    enabled = true,
                    rawJson = site.toString(),
                    lastUpdate = System.currentTimeMillis()
                )
            )
        }
        val lives = parsed.optJSONArray("lives")
        (0 until (lives?.length() ?: 0)).forEach { i ->
            val live = lives!!.getJSONObject(i)
            liveDao.upsert(
                LiveSourceEntity(
                    sourceId = "live-${live.optString("url").ifBlank { UUID.randomUUID().toString() }}",
                    name = live.optString("name", "直播 $i"),
                    url = live.optString("url", ""),
                    epg = live.optString("epg", ""),
                    group = live.optString("group", ""),
                    enabled = true,
                    lastUpdate = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun importFromUrl(name: String, url: String) {
        val body = fetch(url)
        upsertLocal(name, body)
    }

    override suspend fun clear() {
        videoDao.clearAll()
        liveDao.clearAll()
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val resp = client.newCall(
            okhttp3.Request.Builder().url(url).build()
        ).execute()
        resp.use { r ->
            if (!r.isSuccessful) throw AppException.NetworkException(Exception("HTTP ${r.code} $url"))
            r.body?.string() ?: ""
        }
    }
}

class ReaderSourceRepoImpl @javax.inject.Inject constructor(
    private val bookSourceDao: BookSourceDao,
    @ApplicationContext private val context: Context
) : ReaderSourceRepo {
    private val client = OkHttpClient.Builder().build()

    override suspend fun upsertLocal(name: String, bookSourceJson: String) {
        val trimmed = bookSourceJson.trim()
        try {
            if (trimmed.startsWith("[")) {
                // Legado 订阅源导出为 JSON 数组，批量幂等导入
                val arr = org.json.JSONArray(trimmed)
                (0 until arr.length()).forEach { i ->
                    val obj = arr.getJSONObject(i)
                    insertOne(obj.toString())
                }
            } else {
                insertOne(trimmed)
            }
        } catch (e: Exception) {
            throw AppException.RuleParseException(bookSourceJson.take(32), "书源 JSON 无效")
        }
    }

    private suspend fun insertOne(json: String) {
        val obj = org.json.JSONObject(json)
        // 以 bookSourceUrl 为幂等主键，重复导入自动覆盖
        val sourceUrl = obj.optString("bookSourceUrl").ifBlank { "bk-${UUID.randomUUID()}" }
        bookSourceDao.upsert(
            BookSourceEntity(
                sourceId = sourceUrl,
                name = obj.optString("bookSourceName").ifBlank { sourceUrl },
                group = obj.optString("bookSourceGroup", ""),
                url = sourceUrl,
                enabled = true,
                rawJson = json,
                lastUpdate = System.currentTimeMillis()
            )
        )
    }

    override suspend fun importFromUrl(name: String, url: String) {
        val body = withContext(Dispatchers.IO) {
            val resp = client.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute()
            resp.use { r ->
                if (!r.isSuccessful) throw AppException.NetworkException(Exception("HTTP ${r.code} $url"))
                r.body?.string() ?: ""
            }
        }
        upsertLocal(name, body)
    }
}

class MusicSourceRepoImpl @javax.inject.Inject constructor(
    private val musicSourceDao: MusicSourceDao,
    @ApplicationContext private val context: Context
) : MusicSourceRepo {
    private val client = OkHttpClient.Builder().build()

    override suspend fun upsertLocal(name: String, script: String) {
        // 脚本内容真实落盘到应用私有目录，scriptPath 指向本地文件
        val file = writeScriptFile(name, script)
        musicSourceDao.upsert(
            MusicSourceEntity(
                sourceId = "mus-${UUID.randomUUID()}",
                name = name,
                version = "1.0.0",
                scriptPath = file.absolutePath,
                remoteUrl = null,
                enabled = true,
                isBuiltin = false
            )
        )
    }

    override suspend fun importFromUrl(name: String, url: String) {
        val body = withContext(Dispatchers.IO) {
            val resp = client.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute()
            resp.use { r ->
                if (!r.isSuccessful) throw AppException.NetworkException(Exception("HTTP ${r.code} $url"))
                r.body?.string() ?: ""
            }
        }
        val file = writeScriptFile(name, body)
        musicSourceDao.upsert(
            MusicSourceEntity(
                sourceId = "mus-$url",
                name = name,
                version = "1.0.0",
                scriptPath = file.absolutePath,
                remoteUrl = url,
                enabled = true,
                isBuiltin = false
            )
        )
    }

    private fun writeScriptFile(name: String, script: String): java.io.File {
        val dir = context.filesDir.resolve("lxscripts").apply { mkdirs() }
        val safeName = name.replace(Regex("""[^\w\-]"""), "_")
        val file = dir.resolve("$safeName.js")
        file.writeText(script)
        return file
    }
}

class SubscriptionManagerImpl @javax.inject.Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val videoRepo: VideoSourceRepo,
    private val readerRepo: ReaderSourceRepo,
    private val musicRepo: MusicSourceRepo,
    @ApplicationContext private val context: Context
) : SubscriptionManager {

    private val client = OkHttpClient.Builder().build()

    override suspend fun addSubscription(name: String, module: String, url: String) {
        val sub = SubscriptionEntity(
            subId = "sub-${UUID.randomUUID()}",
            name = name,
            moduleType = module,
            url = url,
            lastUpdate = System.currentTimeMillis()
        )
        subscriptionDao.upsert(sub)
        update(sub.subId)
    }

    override suspend fun update(subId: String) {
        val sub = subscriptionDao.byId(subId) ?: return
        if (sub.autoUpdate && System.currentTimeMillis() - sub.lastUpdate < sub.updateInterval) return
        val body = fetch(sub.url)
        if (body.isBlank()) {
            throw AppException.SubscriptionUpdateException(sub.url, Exception("拉取内容为空"))
        }
        when (sub.moduleType) {
            "VIDEO", "LIVE" -> videoRepo.upsertLocal(sub.name, body)
            "READER" -> readerRepo.upsertLocal(sub.name, body)
            "MUSIC" -> musicRepo.upsertLocal(sub.name, body)
            else -> {}
        }
        subscriptionDao.upsert(sub.copy(lastUpdate = System.currentTimeMillis()))
    }

    override suspend fun remove(subId: String) = subscriptionDao.remove(subId)

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val resp = client.newCall(
            okhttp3.Request.Builder().url(url).build()
        ).execute()
        resp.use { r ->
            if (!r.isSuccessful) throw AppException.SubscriptionUpdateException(url, Exception("HTTP ${r.code}"))
            r.body?.string() ?: ""
        }
    }
}
