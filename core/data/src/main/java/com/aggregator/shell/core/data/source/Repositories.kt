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
import com.aggregator.shell.core.data.local.SubscriptionEntity
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
            videoDao.upsert(
                VideoSourceEntity(
                    sourceId = "tvb-${UUID.randomUUID()}",
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
                    sourceId = "live-${UUID.randomUUID()}",
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
        val body = withContext(Dispatchers.IO) {
            val resp = client.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute()
            val text = resp.use { r ->
                if (!r.isSuccessful) {
                    throw AppException.NetworkException(Exception("HTTP ${r.code} $url"))
                }
                r.body?.string() ?: ""
            }
            text
        }
        upsertLocal(name, body)
    }

    override suspend fun clear() {
        videoDao.clearAll()
        liveDao.clearAll()
    }
}

class ReaderSourceRepoImpl @javax.inject.Inject constructor(
    private val bookSourceDao: BookSourceDao,
    @ApplicationContext private val context: Context
) : ReaderSourceRepo {
    private val client = OkHttpClient.Builder().build()

    override suspend fun upsertLocal(name: String, bookSourceJson: String) {
        try {
            val json = org.json.JSONObject(bookSourceJson)
            bookSourceDao.upsert(
                BookSourceEntity(
                    sourceId = "bk-${UUID.randomUUID()}",
                    name = name,
                    group = json.optString("bookSourceGroup", ""),
                    url = json.optString("bookSourceUrl", ""),
                    enabled = true,
                    rawJson = bookSourceJson,
                    lastUpdate = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            throw AppException.RuleParseException(bookSourceJson.take(32), "书源 JSON 无效")
        }
    }

    override suspend fun importFromUrl(name: String, url: String) {
        val body = withContext(Dispatchers.IO) {
            val resp = client.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute()
            val text = resp.use { r ->
                if (!r.isSuccessful) {
                    throw AppException.NetworkException(Exception("HTTP ${r.code} $url"))
                }
                r.body?.string() ?: ""
            }
            text
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
        musicSourceDao.upsert(
            MusicSourceEntity(
                sourceId = "mus-${UUID.randomUUID()}",
                name = name,
                version = "1.0.0",
                scriptPath = "shell://local/$name.js",
                remoteUrl = null,
                enabled = true,
                isBuiltin = false
            )
        )
    }

    override suspend fun importFromUrl(name: String, url: String) {
        musicSourceDao.upsert(
            MusicSourceEntity(
                sourceId = "mus-${UUID.randomUUID()}",
                name = name,
                version = "1.0.0",
                scriptPath = url,
                remoteUrl = url,
                enabled = true,
                isBuiltin = false
            )
        )
    }
}

class SubscriptionManagerImpl @javax.inject.Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val videoRepo: VideoSourceRepo,
    private val readerRepo: ReaderSourceRepo,
    private val musicRepo: MusicSourceRepo,
    @ApplicationContext private val context: Context
) : SubscriptionManager {

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
        // TODO: look up by subId and pull URL. Placeholder for now.
    }

    override suspend fun remove(subId: String) = subscriptionDao.remove(subId)
}
