package com.worklogai.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `todo_items` (
                    `id` TEXT NOT NULL,
                    `scheduledDate` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `note` TEXT,
                    `priority` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `completionNote` TEXT,
                    `linkedContentBlockId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`linkedContentBlockId`) REFERENCES `content_blocks`(`id`)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_todo_items_scheduledDate` " +
                    "ON `todo_items` (`scheduledDate`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_todo_items_scheduledDate_status` " +
                    "ON `todo_items` (`scheduledDate`, `status`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_todo_items_scheduledDate_priority_sortOrder` " +
                    "ON `todo_items` (`scheduledDate`, `priority`, `sortOrder`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_todo_items_linkedContentBlockId` " +
                    "ON `todo_items` (`linkedContentBlockId`)",
            )
        }
    }
