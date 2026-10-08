package com.aggregator.shell.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aggregator.shell.core.data.local.entity.FavoriteEntity
import com.aggregator.shell.core.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room schema migration 测试（硬伤 1）。
 * 校验 v1 -> v2 显式 [Migrations.MIGRATION_1_2] 后，新表（favorites /
 * search_history）可正常读写，既有表结构保持不变。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migrateV1ToV2_createsFavoriteAndSearchHistoryTables() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // 以 v1 schema 建库并插入既有表数据
        val v1 = Room.databaseBuilder(ctx, AppDatabase::class.java, "migrate_v1")
            .createFromAsset("schemas/com.aggregator.shell.core.data.local.AppDatabase/1.json")
            .allowInvalidCreateStatements(true)
            .openHelperFactory(androidx.room.testing.RoomDatabaseTestOpenHelperFactory())
            .build()
        runBlocking {
            v1.subscriptionDao().upsert(
                com.aggregator.shell.core.data.local.entity.SubscriptionEntity(
                    subId = "sub-1", name = "tv", moduleType = "VIDEO", url = "http://x"
                )
            )
            v1.close()
        }

        // 应用显式 Migration 升到 v2
        val v2 = Room.databaseBuilder(ctx, AppDatabase::class.java, "migrate_v1")
            .createFromAsset("schemas/com.aggregator.shell.core.data.local.AppDatabase/1.json")
            .addMigrations(Migrations.MIGRATION_1_2)
            .allowInvalidCreateStatements(true)
            .openHelperFactory(androidx.room.testing.RoomDatabaseTestOpenHelperFactory())
            .build()

        // 既有数据保留
        assertEquals(
            "sub-1",
            runBlocking { v2.subscriptionDao().byId("sub-1")?.subId }
        )
        // 新表可写
        runBlocking {
            v2.favoriteDao().upsert(
                FavoriteEntity(id = "fav-1", module = "VIDEO", sourceId = "s", contentId = "c", title = "t")
            )
            v2.searchHistoryDao().upsert(
                SearchHistoryEntity(id = "sh-1", module = "VIDEO", keyword = "斗破")
            )
        }
        val fav = runBlocking { v2.favoriteDao().find("VIDEO", "c") }
        assertNotNull(fav)
        assertEquals("t", fav?.title)
        val sh = runBlocking { v2.searchHistoryDao().recent() }.first()
        assertEquals(1, sh.size)
        assertEquals("斗破", sh[0].keyword)

        v2.close()
    }
}
