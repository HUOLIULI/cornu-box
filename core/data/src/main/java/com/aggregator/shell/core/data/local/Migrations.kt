package com.aggregator.shell.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * schema v1 -> v2：新增 favorites / search_history 两表（收藏 + 搜索历史）。
 *
 * 仅 additive（新建表），无既有列变更，安全迁移。Room 校验由
 * [androidx.room.RoomMigrationTestHelper] 在 migration 测试中断言。
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorites` (
                    id TEXT NOT NULL PRIMARY KEY,
                    module TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    contentId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    subInfo TEXT NOT NULL DEFAULT '',
                    favoriteTime INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_history` (
                    id TEXT NOT NULL PRIMARY KEY,
                    module TEXT NOT NULL,
                    keyword TEXT NOT NULL,
                    ts INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }
}
