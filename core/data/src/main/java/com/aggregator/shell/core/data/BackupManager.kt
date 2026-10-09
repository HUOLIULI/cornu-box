package com.aggregator.shell.core.data

import android.content.Context
import android.net.Uri
import com.aggregator.shell.core.data.local.AppDatabase
import com.aggregator.shell.core.data.local.entity.*
import com.aggregator.shell.core.data.di.appDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 全量备份/恢复管理器
 * 将 Room 数据库所有表 + DataStore 配置导出为单个 JSON 文件，支持反向导入
 */
class BackupManager @javax.inject.Inject constructor(
    private val db: AppDatabase,
    @ApplicationContext private val context: Context
) {
    data class BackupData(
        val version: Int = 1,
        val exportTime: Long = System.currentTimeMillis(),
        val bookSources: List<BookSourceEntity> = emptyList(),
        val bookshelf: List<BookshelfEntity> = emptyList(),
        val videoSources: List<VideoSourceEntity> = emptyList(),
        val liveSources: List<LiveSourceEntity> = emptyList(),
        val musicSources: List<MusicSourceEntity> = emptyList(),
        val epgChannels: List<EpgChannelEntity> = emptyList(),
        val epgPrograms: List<EpgProgramEntity> = emptyList(),
        val playHistory: List<PlayHistoryEntity> = emptyList(),
        val subscriptions: List<SubscriptionEntity> = emptyList(),
        val sourceLogs: List<SourceLogEntity> = emptyList(),
        val favorites: List<FavoritesEntity> = emptyList(),
        val dataStore: Map<String, Any?> = emptyMap()
    )

    /**
     * 导出全量备份到指定 URI（ContentResolver）
     */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = collectAllData()
        val json = serializeToJson(backup)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw java.io.IOException("无法写入 URI: $uri")
        json.length
    }

    /**
     * 导出到本地文件（应用私有目录）
     */
    suspend fun exportToLocalFile(): File = withContext(Dispatchers.IO) {
        val backup = collectAllData()
        val json = serializeToJson(backup)
        val dir = context.filesDir.resolve("backups").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val file = dir.resolve("backup_$timestamp.json")
        file.writeText(json, Charsets.UTF_8)
        file
    }

    /**
     * 从指定 URI 恢复备份
     */
    suspend fun restore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
        } ?: return@withContext false
        restoreFromJson(json)
        true
    }

    /**
     * 从本地文件恢复备份
     */
    suspend fun restoreFromLocalFile(file: File): Boolean = withContext(Dispatchers.IO) {
        val json = file.readText(Charsets.UTF_8)
        restoreFromJson(json)
        true
    }

    /**
     * 收集所有数据
     */
    private suspend fun collectAllData(): BackupData = withContext(Dispatchers.IO) {
        val prefs = context.appDataStore.data.first()
        val prefsMap = mutableMapOf<String, Any?>()
        // 手动收集我们关心的 DataStore 键
        val keyMap = mapOf(
            "llm_base_url" to "llmBaseUrl",
            "llm_api_key" to "llmApiKey",
            "danmaku_base_url" to "danmakuBaseUrl",
            "danmaku_api_key" to "danmakuApiKey",
            "dark_mode" to "darkMode"
        )
        for ((kotlinKey, jsonKey) in keyMap) {
            val key = when (kotlinKey) {
                "dark_mode" -> androidx.datastore.preferences.core.booleanPreferencesKey(kotlinKey)
                else -> androidx.datastore.preferences.core.stringPreferencesKey(kotlinKey)
            }
            if (prefs.contains(key)) {
                prefsMap[jsonKey] = prefs[key]
            }
        }

        BackupData(
            bookSources = db.bookSourceDao().all().first(),
            bookshelf = db.bookshelfDao().all().first(),
            videoSources = db.videoSourceDao().all().first(),
            liveSources = db.liveSourceDao().all().first(),
            musicSources = db.musicSourceDao().all().first(),
            epgChannels = db.epgDao().channels().first(),
            epgPrograms = flattenEpgPrograms(),
            playHistory = collectPlayHistory(),
            subscriptions = db.subscriptionDao().all().first(),
            sourceLogs = db.sourceLogDao().recent().first(),
            favorites = db.favoritesDao().all().first(),
            dataStore = prefsMap
        )
    }

    private suspend fun flattenEpgPrograms(): List<EpgProgramEntity> {
        val channels = db.epgDao().channels().first()
        return channels.flatMap { channel ->
            db.epgDao().programs(channel.channelId).first()
        }
    }

    private suspend fun collectPlayHistory(): List<PlayHistoryEntity> {
        return db.playHistoryDao().all()
    }

    /**
     * 序列化为 JSON 字符串
     */
    private fun serializeToJson(backup: BackupData): String {
        val root = JSONObject()
        root.put("version", backup.version)
        root.put("exportTime", backup.exportTime)

        // bookSources
        val bsArr = JSONArray()
        for (e in backup.bookSources) {
            bsArr.put(JSONObject().apply {
                put("sourceId", e.sourceId)
                put("name", e.name)
                put("group", e.group)
                put("url", e.url)
                put("enabled", e.enabled)
                put("rawJson", e.rawJson)
                put("sortOrder", e.sortOrder)
                put("lastUpdate", e.lastUpdate)
                put("lastTestResult", e.lastTestResult ?: "")
            })
        }
        root.put("bookSources", bsArr)

        // bookshelf
        val bkArr = JSONArray()
        for (e in backup.bookshelf) {
            bkArr.put(JSONObject().apply {
                put("bookId", e.bookId)
                put("sourceId", e.sourceId)
                put("name", e.name)
                put("author", e.author)
                put("coverUrl", e.coverUrl)
                put("lastChapter", e.lastChapter)
                put("lastReadTime", e.lastReadTime)
                put("readProgress", e.readProgress)
            })
        }
        root.put("bookshelf", bkArr)

        // videoSources
        val vsArr = JSONArray()
        for (e in backup.videoSources) {
            vsArr.put(JSONObject().apply {
                put("sourceId", e.sourceId)
                put("name", e.name)
                put("api", e.api)
                put("spider", e.spider)
                put("ext", e.ext)
                put("enabled", e.enabled)
                put("rawJson", e.rawJson)
                put("lastUpdate", e.lastUpdate)
            })
        }
        root.put("videoSources", vsArr)

        // liveSources
        val lsArr = JSONArray()
        for (e in backup.liveSources) {
            lsArr.put(JSONObject().apply {
                put("sourceId", e.sourceId)
                put("name", e.name)
                put("url", e.url)
                put("epg", e.epg)
                put("group", e.group)
                put("enabled", e.enabled)
                put("lastUpdate", e.lastUpdate)
            })
        }
        root.put("liveSources", lsArr)

        // musicSources
        val msArr = JSONArray()
        for (e in backup.musicSources) {
            msArr.put(JSONObject().apply {
                put("sourceId", e.sourceId)
                put("name", e.name)
                put("version", e.version)
                put("author", e.author)
                put("description", e.description)
                put("scriptPath", e.scriptPath)
                put("remoteUrl", e.remoteUrl ?: "")
                put("enabled", e.enabled)
                put("isBuiltin", e.isBuiltin)
            })
        }
        root.put("musicSources", msArr)

        // epgChannels
        val ecArr = JSONArray()
        for (e in backup.epgChannels) {
            ecArr.put(JSONObject().apply {
                put("channelId", e.channelId)
                put("displayName", e.displayName)
                put("iconUrl", e.iconUrl ?: "")
            })
        }
        root.put("epgChannels", ecArr)

        // epgPrograms
        val epArr = JSONArray()
        for (e in backup.epgPrograms) {
            epArr.put(JSONObject().apply {
                put("id", e.id)
                put("channelId", e.channelId)
                put("title", e.title)
                put("startTime", e.startTime)
                put("endTime", e.endTime)
                put("description", e.description ?: "")
                put("iconUrl", e.iconUrl ?: "")
            })
        }
        root.put("epgPrograms", epArr)

        // playHistory
        val phArr = JSONArray()
        for (e in backup.playHistory) {
            phArr.put(JSONObject().apply {
                put("id", e.id)
                put("sourceId", e.sourceId)
                put("contentId", e.contentId)
                put("title", e.title)
                put("positionMs", e.positionMs)
                put("module", e.module)
                put("updated", e.updated)
            })
        }
        root.put("playHistory", phArr)

        // subscriptions
        val subArr = JSONArray()
        for (e in backup.subscriptions) {
            subArr.put(JSONObject().apply {
                put("subId", e.subId)
                put("name", e.name)
                put("moduleType", e.moduleType)
                put("url", e.url)
                put("updateInterval", e.updateInterval)
                put("lastUpdate", e.lastUpdate)
                put("autoUpdate", e.autoUpdate)
                put("sourceCount", e.sourceCount)
                put("rawJson", e.rawJson)
            })
        }
        root.put("subscriptions", subArr)

        // sourceLogs
        val logArr = JSONArray()
        for (e in backup.sourceLogs) {
            logArr.put(JSONObject().apply {
                put("id", e.id)
                put("ts", e.ts)
                put("category", e.category)
                put("url", e.url ?: "")
                put("method", e.method ?: "")
                put("status", e.status)
                put("detail", e.detail)
                put("durationMs", e.durationMs)
            })
        }
        root.put("sourceLogs", logArr)

        // favorites
        val favArr = JSONArray()
        for (e in backup.favorites) {
            favArr.put(JSONObject().apply {
                put("id", e.id)
                put("sourceId", e.sourceId)
                put("contentId", e.contentId)
                put("title", e.title)
                put("coverUrl", e.coverUrl ?: "")
                put("module", e.module)
                put("addedTime", e.addedTime)
                put("category", e.category)
            })
        }
        root.put("favorites", favArr)

        // dataStore
        val dsObj = JSONObject()
        for ((k, v) in backup.dataStore) {
            if (v != null) {
                dsObj.put(k, v)
            }
        }
        root.put("dataStore", dsObj)

        return root.toString()
    }

    /**
     * 从 JSON 字符串恢复数据
     */
    private suspend fun restoreFromJson(json: String): Unit = withContext(Dispatchers.IO) {
        val root = JSONObject(json)

        // 清空所有表
        db.bookSourceDao().clearAll()
        db.videoSourceDao().clearAll()
        db.liveSourceDao().clearAll()
        db.musicSourceDao().clearAll()
        db.epgDao().clearChannels()
        db.epgDao().clearPrograms()
        db.sourceLogDao().clearAll()
        db.favoritesDao().clearAll()

        // 恢复 bookSources
        val bsArr = root.optJSONArray("bookSources")
        if (bsArr != null) {
            for (i in 0 until bsArr.length()) {
                val obj = bsArr.getJSONObject(i)
                db.bookSourceDao().upsert(
                    BookSourceEntity(
                        sourceId = obj.getString("sourceId"),
                        name = obj.getString("name"),
                        group = obj.optString("group", ""),
                        url = obj.getString("url"),
                        enabled = obj.optBoolean("enabled", true),
                        rawJson = obj.optString("rawJson", "{}"),
                        sortOrder = obj.optInt("sortOrder", 0),
                        lastUpdate = obj.optLong("lastUpdate", 0L),
                        lastTestResult = obj.optString("lastTestResult", null).ifEmpty { null }
                    )
                )
            }
        }

        // 恢复 bookshelf
        val bkArr = root.optJSONArray("bookshelf")
        if (bkArr != null) {
            for (i in 0 until bkArr.length()) {
                val obj = bkArr.getJSONObject(i)
                db.bookshelfDao().upsert(
                    BookshelfEntity(
                        bookId = obj.getString("bookId"),
                        sourceId = obj.getString("sourceId"),
                        name = obj.getString("name"),
                        author = obj.getString("author"),
                        coverUrl = obj.getString("coverUrl"),
                        lastChapter = obj.optString("lastChapter", ""),
                        lastReadTime = obj.optLong("lastReadTime", 0L),
                        readProgress = obj.optDouble("readProgress", 0.0).toFloat()
                    )
                )
            }
        }

        // 恢复 videoSources
        val vsArr = root.optJSONArray("videoSources")
        if (vsArr != null) {
            for (i in 0 until vsArr.length()) {
                val obj = vsArr.getJSONObject(i)
                db.videoSourceDao().upsert(
                    VideoSourceEntity(
                        sourceId = obj.getString("sourceId"),
                        name = obj.getString("name"),
                        api = obj.getString("api"),
                        spider = obj.optString("spider", ""),
                        ext = obj.optString("ext", ""),
                        enabled = obj.optBoolean("enabled", true),
                        rawJson = obj.optString("rawJson", "{}"),
                        lastUpdate = obj.optLong("lastUpdate", 0L)
                    )
                )
            }
        }

        // 恢复 liveSources
        val lsArr = root.optJSONArray("liveSources")
        if (lsArr != null) {
            for (i in 0 until lsArr.length()) {
                val obj = lsArr.getJSONObject(i)
                db.liveSourceDao().upsert(
                    LiveSourceEntity(
                        sourceId = obj.getString("sourceId"),
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        epg = obj.optString("epg", ""),
                        group = obj.optString("group", ""),
                        enabled = obj.optBoolean("enabled", true),
                        lastUpdate = obj.optLong("lastUpdate", 0L)
                    )
                )
            }
        }

        // 恢复 musicSources
        val msArr = root.optJSONArray("musicSources")
        if (msArr != null) {
            for (i in 0 until msArr.length()) {
                val obj = msArr.getJSONObject(i)
                db.musicSourceDao().upsert(
                    MusicSourceEntity(
                        sourceId = obj.getString("sourceId"),
                        name = obj.getString("name"),
                        version = obj.optString("version", "1.0.0"),
                        author = obj.optString("author", ""),
                        description = obj.optString("description", ""),
                        scriptPath = obj.getString("scriptPath"),
                        remoteUrl = obj.optString("remoteUrl", null).ifEmpty { null },
                        enabled = obj.optBoolean("enabled", true),
                        isBuiltin = obj.optBoolean("isBuiltin", false)
                    )
                )
            }
        }

        // 恢复 epgChannels
        val ecArr = root.optJSONArray("epgChannels")
        if (ecArr != null) {
            val channels = mutableListOf<EpgChannelEntity>()
            for (i in 0 until ecArr.length()) {
                val obj = ecArr.getJSONObject(i)
                channels.add(
                    EpgChannelEntity(
                        channelId = obj.getString("channelId"),
                        displayName = obj.getString("displayName"),
                        iconUrl = obj.optString("iconUrl", null).ifEmpty { null }
                    )
                )
            }
            if (channels.isNotEmpty()) {
                db.epgDao().upsertChannels(channels)
            }
        }

        // 恢复 epgPrograms
        val epArr = root.optJSONArray("epgPrograms")
        if (epArr != null) {
            val programs = mutableListOf<EpgProgramEntity>()
            for (i in 0 until epArr.length()) {
                val obj = epArr.getJSONObject(i)
                programs.add(
                    EpgProgramEntity(
                        id = obj.getString("id"),
                        channelId = obj.getString("channelId"),
                        title = obj.getString("title"),
                        startTime = obj.getLong("startTime"),
                        endTime = obj.getLong("endTime"),
                        description = obj.optString("description", null).ifEmpty { null },
                        iconUrl = obj.optString("iconUrl", null).ifEmpty { null }
                    )
                )
            }
            if (programs.isNotEmpty()) {
                db.epgDao().upsertPrograms(programs)
            }
        }

        // 恢复 playHistory
        val phArr = root.optJSONArray("playHistory")
        if (phArr != null) {
            for (i in 0 until phArr.length()) {
                val obj = phArr.getJSONObject(i)
                db.playHistoryDao().upsert(
                    PlayHistoryEntity(
                        id = obj.getString("id"),
                        sourceId = obj.getString("sourceId"),
                        contentId = obj.getString("contentId"),
                        title = obj.getString("title"),
                        positionMs = obj.optLong("positionMs", 0L),
                        module = obj.getString("module"),
                        updated = obj.optLong("updated", 0L)
                    )
                )
            }
        }

        // 恢复 subscriptions
        val subArr = root.optJSONArray("subscriptions")
        if (subArr != null) {
            for (i in 0 until subArr.length()) {
                val obj = subArr.getJSONObject(i)
                db.subscriptionDao().upsert(
                    SubscriptionEntity(
                        subId = obj.getString("subId"),
                        name = obj.getString("name"),
                        moduleType = obj.getString("moduleType"),
                        url = obj.getString("url"),
                        updateInterval = obj.optLong("updateInterval", 86_400_000L),
                        lastUpdate = obj.optLong("lastUpdate", 0L),
                        autoUpdate = obj.optBoolean("autoUpdate", true),
                        sourceCount = obj.optInt("sourceCount", 0),
                        rawJson = obj.optString("rawJson", "")
                    )
                )
            }
        }

        // 恢复 sourceLogs
        val logArr = root.optJSONArray("sourceLogs")
        if (logArr != null) {
            for (i in 0 until logArr.length()) {
                val obj = logArr.getJSONObject(i)
                db.sourceLogDao().add(
                    SourceLogEntity(
                        id = obj.getString("id"),
                        ts = obj.getLong("ts"),
                        category = obj.getString("category"),
                        url = obj.optString("url", null).ifEmpty { null },
                        method = obj.optString("method", null).ifEmpty { null },
                        status = obj.optInt("status", 0),
                        detail = obj.optString("detail", ""),
                        durationMs = obj.optLong("durationMs", 0L)
                    )
                )
            }
        }

        // 恢复 favorites
        val favArr = root.optJSONArray("favorites")
        if (favArr != null) {
            for (i in 0 until favArr.length()) {
                val obj = favArr.getJSONObject(i)
                db.favoritesDao().upsert(
                    FavoritesEntity(
                        id = obj.getString("id"),
                        sourceId = obj.getString("sourceId"),
                        contentId = obj.getString("contentId"),
                        title = obj.getString("title"),
                        coverUrl = obj.optString("coverUrl", null).ifEmpty { null },
                        module = obj.getString("module"),
                        addedTime = obj.optLong("addedTime", 0L),
                        category = obj.optString("category", "default")
                    )
                )
            }
        }

        // 恢复 dataStore
        val dsObj = root.optJSONObject("dataStore")
        if (dsObj != null) {
            context.appDataStore.edit { prefs ->
                for (key in dsObj.keys()) {
                    val value = dsObj.get(key)
                    when (key) {
                        "llmBaseUrl" -> prefs[androidx.datastore.preferences.core.stringPreferencesKey("llm_base_url")] =
                            value.toString()
                        "llmApiKey" -> prefs[androidx.datastore.preferences.core.stringPreferencesKey("llm_api_key")] =
                            value.toString()
                        "danmakuBaseUrl" -> prefs[androidx.datastore.preferences.core.stringPreferencesKey("danmaku_base_url")] =
                            value.toString()
                        "danmakuApiKey" -> prefs[androidx.datastore.preferences.core.stringPreferencesKey("danmaku_api_key")] =
                            value.toString()
                        "darkMode" -> {
                            val boolValue = when (value) {
                                is Boolean -> value
                                is String -> value.toBoolean()
                                else -> false
                            }
                            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("dark_mode")] = boolValue
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取备份文件列表（应用私有目录）
     */
    fun listBackups(): List<File> {
        val dir = context.filesDir.resolve("backups")
        return dir.listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * 删除指定备份文件
     */
    fun deleteBackup(file: File): Boolean {
        return file.delete()
    }
}
