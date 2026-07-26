package com.worklogai.app.core.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WorkLogMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migration 1 to 2 preserves every existing table and creates todo schema`() =
        runBlocking {
            createVersionOneDatabase()

            val database =
                Room
                    .databaseBuilder(context, WorkLogDatabase::class.java, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()

            try {
                assertEquals(2, database.openHelper.writableDatabase.version)
                assertEquals(
                    "entry",
                    database
                        .workEntryDao()
                        .getAllIncludingDeleted()
                        .single()
                        .id,
                )
                assertEquals(
                    "block",
                    database
                        .contentBlockDao()
                        .getAll()
                        .single()
                        .id,
                )
                assertEquals(
                    "attachment",
                    database
                        .attachmentDao()
                        .getAll()
                        .single()
                        .id,
                )
                assertEquals(
                    "summary",
                    database
                        .workSummaryBackupDao()
                        .getAll()
                        .single()
                        .id,
                )
                assertTrue(database.todoDao().getAll().isEmpty())

                val sqlite = database.openHelper.writableDatabase
                val indexNames =
                    sqlite.query("PRAGMA index_list(`todo_items`)").use { cursor ->
                        buildSet {
                            val nameColumn = cursor.getColumnIndexOrThrow("name")
                            while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                        }
                    }
                assertTrue(indexNames.containsAll(TODO_INDEX_NAMES))
                sqlite.query("PRAGMA foreign_key_list(`todo_items`)").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("content_blocks", cursor.getString(cursor.getColumnIndexOrThrow("table")))
                    assertEquals("SET NULL", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
                }
                assertNotNull(database.todoDao())
            } finally {
                database.close()
            }
        }

    private fun createVersionOneDatabase() {
        val file = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            VERSION_ONE_SCHEMA.forEach(database::execSQL)
            database.execSQL(
                "INSERT INTO work_entries VALUES " +
                    "('entry', 20658, '基线', 1, 0, 1784880000000, 1784880000000)",
            )
            database.execSQL(
                "INSERT INTO content_blocks VALUES " +
                    "('block', 'entry', 'IMAGE', 0, NULL, NULL, 1784880000000, 1784880000000)",
            )
            database.execSQL(
                "INSERT INTO attachments VALUES " +
                    "('attachment', 'block', 'images/test.jpg', 'image/jpeg', 3, 1, 1, '说明', 1784880000000)",
            )
            database.execSQL(
                "INSERT INTO work_summaries VALUES " +
                    "('summary', 'WEEKLY', 20652, 20658, 'SUCCESS', NULL, NULL, NULL, " +
                    "'原文', '编辑', NULL, 1784880000000, 1784880000000, 1784880000000)",
            )
            database.version = 1
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-1-2.db"

        val TODO_INDEX_NAMES =
            setOf(
                "index_todo_items_scheduledDate",
                "index_todo_items_scheduledDate_status",
                "index_todo_items_scheduledDate_priority_sortOrder",
                "index_todo_items_linkedContentBlockId",
            )

        val VERSION_ONE_SCHEMA =
            listOf(
                "CREATE TABLE work_entries (" +
                    "id TEXT NOT NULL PRIMARY KEY, entryDate INTEGER NOT NULL, title TEXT, " +
                    "allowAiProcessing INTEGER NOT NULL, isDeleted INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                "CREATE UNIQUE INDEX index_work_entries_entryDate ON work_entries(entryDate)",
                "CREATE TABLE content_blocks (" +
                    "id TEXT NOT NULL PRIMARY KEY, entryId TEXT NOT NULL, blockType TEXT NOT NULL, " +
                    "blockOrder INTEGER NOT NULL, textContent TEXT, structuredContent TEXT, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(entryId) REFERENCES work_entries(id) ON DELETE CASCADE)",
                "CREATE INDEX index_content_blocks_entryId ON content_blocks(entryId)",
                "CREATE INDEX index_content_blocks_entryId_blockOrder ON content_blocks(entryId, blockOrder)",
                "CREATE TABLE attachments (" +
                    "id TEXT NOT NULL PRIMARY KEY, blockId TEXT NOT NULL, localPath TEXT NOT NULL, " +
                    "mimeType TEXT NOT NULL, fileSize INTEGER NOT NULL, width INTEGER, height INTEGER, " +
                    "caption TEXT, createdAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(blockId) REFERENCES content_blocks(id) ON DELETE CASCADE)",
                "CREATE INDEX index_attachments_blockId ON attachments(blockId)",
                "CREATE TABLE work_summaries (" +
                    "id TEXT NOT NULL PRIMARY KEY, summaryType TEXT NOT NULL, periodStart INTEGER NOT NULL, " +
                    "periodEnd INTEGER NOT NULL, status TEXT NOT NULL, sourceHash TEXT, aiProvider TEXT, " +
                    "modelName TEXT, originalContent TEXT, editedContent TEXT, errorMessage TEXT, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, generatedAt INTEGER)",
                "CREATE INDEX index_work_summaries_summaryType ON work_summaries(summaryType)",
                "CREATE UNIQUE INDEX index_work_summaries_summaryType_periodStart_periodEnd " +
                    "ON work_summaries(summaryType, periodStart, periodEnd)",
            )
    }
}
