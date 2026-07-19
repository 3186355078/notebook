package com.worklogai.app.app

import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DeviceScaleIntegrationTest {
    @Test
    fun thousandEntriesFiveThousandBlocksAndLargeBackupRemainQueryableAndRestorable() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val original = ByteArrayOutputStream()
            assertTrue(dependencies.backupArchiveService().createBackup(original) is BackupOperationResult.Success)

            try {
                dependencies.database().clearAllTables()
                seedScaleData(dependencies)

                val historyStarted = SystemClock.elapsedRealtime()
                val firstPage = dependencies.historyRepository().getRecentEntries(PAGE_SIZE, 0) as DataResult.Success
                val historyMillis = SystemClock.elapsedRealtime() - historyStarted
                assertEquals(PAGE_SIZE, firstPage.value.entries.size)
                assertEquals(PAGE_SIZE, firstPage.value.nextOffset)

                val secondPage =
                    dependencies.historyRepository().getRecentEntries(PAGE_SIZE, PAGE_SIZE) as DataResult.Success
                assertEquals(PAGE_SIZE, secondPage.value.entries.size)
                assertTrue(
                    firstPage.value.entries
                        .map { it.entryId }
                        .toSet()
                        .intersect(
                            secondPage.value.entries
                                .map { it.entryId }
                                .toSet(),
                        ).isEmpty(),
                )
                assertTrue(
                    (firstPage.value.entries + secondPage.value.entries)
                        .zipWithNext()
                        .all { (left, right) -> left.date >= right.date },
                )

                val searchStarted = SystemClock.elapsedRealtime()
                val textSearch = dependencies.historyRepository().searchEntries("stage9-text-token-999", PAGE_SIZE, 0)
                val tableSearch = dependencies.historyRepository().searchEntries(TABLE_TOKEN, PAGE_SIZE, 0)
                val captionSearch = dependencies.historyRepository().searchEntries(CAPTION_TOKEN, PAGE_SIZE, 0)
                val searchMillis = SystemClock.elapsedRealtime() - searchStarted
                assertEquals(1, (textSearch as DataResult.Success).value.entries.size)
                assertTrue((tableSearch as DataResult.Success).value.entries.isNotEmpty())
                assertTrue((captionSearch as DataResult.Success).value.entries.isNotEmpty())

                val archive = ByteArrayOutputStream()
                val backupStarted = SystemClock.elapsedRealtime()
                val created = dependencies.backupArchiveService().createBackup(archive)
                val backupMillis = SystemClock.elapsedRealtime() - backupStarted
                assertTrue(created is BackupOperationResult.Success)
                assertEquals(0, (created as BackupOperationResult.Success).value.warningCount)

                val previewStarted = SystemClock.elapsedRealtime()
                val preview =
                    dependencies.backupArchiveService().inspectBackup(
                        ByteArrayInputStream(archive.toByteArray()),
                    )
                val previewMillis = SystemClock.elapsedRealtime() - previewStarted
                assertTrue(preview is BackupOperationResult.Success)
                val previewValue = (preview as BackupOperationResult.Success).value
                assertEquals(ENTRY_COUNT, previewValue.entryCount)
                assertEquals(BLOCK_COUNT, previewValue.blockCount)
                assertEquals(IMAGE_COUNT, previewValue.attachmentCount)
                assertEquals(SUMMARY_COUNT, previewValue.summaryCount)

                dependencies.database().clearAllTables()
                val restoreStarted = SystemClock.elapsedRealtime()
                val restored =
                    dependencies.backupArchiveService().restoreBackup(
                        ByteArrayInputStream(archive.toByteArray()),
                    )
                val restoreMillis = SystemClock.elapsedRealtime() - restoreStarted
                assertTrue(restored is BackupOperationResult.Success)
                val afterRestore = dependencies.historyRepository().getRecentEntries(PAGE_SIZE, 0) as DataResult.Success
                assertEquals(PAGE_SIZE, afterRestore.value.entries.size)
                assertNotNull(dependencies.database().attachmentDao().getById("stage9-scale-attachment-995"))

                Log.i(
                    METRICS_TAG,
                    "history_ms=$historyMillis search_ms=$searchMillis backup_ms=$backupMillis " +
                        "preview_ms=$previewMillis restore_ms=$restoreMillis archive_bytes=${archive.size()} " +
                        "pss_kb=${Debug.getPss()}",
                )
            } finally {
                val restoredOriginal =
                    dependencies
                        .backupArchiveService()
                        .restoreBackup(ByteArrayInputStream(original.toByteArray()))
                assertTrue(restoredOriginal is BackupOperationResult.Success)
            }
        }
    }

    private suspend fun seedScaleData(dependencies: Stage9TestEntryPoint) {
        val database = dependencies.database()
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val tableJson =
            (
                KotlinxTableContentCodec().encode(
                    TableContent(
                        title = TABLE_TOKEN,
                        columns = listOf(TableColumn("value", "Value")),
                        rows = listOf(TableRow("row", mapOf("value" to TABLE_TOKEN))),
                    ),
                ) as DataResult.Success
            ).value
        val entries = ArrayList<WorkEntryEntity>(ENTRY_COUNT)
        val blocks = ArrayList<ContentBlockEntity>(BLOCK_COUNT)
        val attachments = ArrayList<AttachmentEntity>(IMAGE_COUNT)
        val imageBytes = createControlledPng()
        val imageRoot =
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir.resolve(
                "attachments/images",
            )
        imageRoot.mkdirs()

        repeat(ENTRY_COUNT) { entryIndex ->
            val entryId = "stage9-scale-entry-$entryIndex"
            entries +=
                WorkEntryEntity(
                    id = entryId,
                    entryDate = SCALE_START.plusDays(entryIndex.toLong()),
                    title = "Stage 9 scale entry $entryIndex",
                    allowAiProcessing = true,
                    isDeleted = false,
                    createdAt = now,
                    updatedAt = now,
                )
            repeat(BLOCKS_PER_ENTRY) { blockIndex ->
                val isTable = blockIndex == 2 && entryIndex % IMAGE_INTERVAL == 0
                val isImage = blockIndex == 3 && entryIndex % IMAGE_INTERVAL == 0
                val type =
                    when {
                        isTable -> ContentBlockType.TABLE
                        isImage -> ContentBlockType.IMAGE
                        else -> ContentBlockType.TEXT
                    }
                val blockId = "stage9-scale-block-$entryIndex-$blockIndex"
                blocks +=
                    ContentBlockEntity(
                        id = blockId,
                        entryId = entryId,
                        blockType = type,
                        blockOrder = blockIndex,
                        textContent =
                            if (type ==
                                ContentBlockType.TEXT
                            ) {
                                "stage9-text-token-$entryIndex-$blockIndex"
                            } else {
                                null
                            },
                        structuredContent = if (type == ContentBlockType.TABLE) tableJson else null,
                        createdAt = now,
                        updatedAt = now,
                    )
                if (isImage) {
                    val relativePath = "images/stage9-scale-$entryIndex.png"
                    imageRoot.resolve("stage9-scale-$entryIndex.png").writeBytes(imageBytes)
                    attachments +=
                        AttachmentEntity(
                            id = "stage9-scale-attachment-$entryIndex",
                            blockId = blockId,
                            localPath = relativePath,
                            mimeType = "image/png",
                            fileSize = imageBytes.size.toLong(),
                            width = TEST_IMAGE_EDGE,
                            height = TEST_IMAGE_EDGE,
                            caption = "$CAPTION_TOKEN $entryIndex",
                            createdAt = now,
                        )
                }
            }
        }
        val summaries =
            (0 until SUMMARY_COUNT).map { index ->
                val month = SUMMARY_START.plusMonths(index.toLong())
                WorkSummaryEntity(
                    id = "stage9-scale-summary-$index",
                    summaryType = SummaryType.MONTHLY,
                    periodStart = month.atDay(1),
                    periodEnd = month.atEndOfMonth(),
                    status = SummaryStatus.SUCCESS,
                    sourceHash = "0".repeat(64),
                    aiProvider = "mock",
                    modelName = "mock-work-summary-v1",
                    originalContent = "{}",
                    editedContent = "Stage 9 scale summary $index",
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now,
                    generatedAt = now,
                )
            }

        database.withTransaction {
            database.workEntryDao().insertAll(entries)
            database.contentBlockDao().insertAll(blocks)
            database.attachmentDao().insertAll(attachments)
            database.workSummaryBackupDao().insertAll(summaries)
        }
    }

    private fun createControlledPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(TEST_IMAGE_EDGE, TEST_IMAGE_EDGE, Bitmap.Config.ARGB_8888)
        val pixels =
            IntArray(TEST_IMAGE_EDGE * TEST_IMAGE_EDGE) { index ->
                val mixed = index * 1_103_515_245 + 12_345
                0xff000000.toInt() or (mixed and 0x00ffffff)
            }
        bitmap.setPixels(pixels, 0, TEST_IMAGE_EDGE, 0, 0, TEST_IMAGE_EDGE, TEST_IMAGE_EDGE)
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val ENTRY_COUNT = 1_000
        const val BLOCKS_PER_ENTRY = 5
        const val BLOCK_COUNT = ENTRY_COUNT * BLOCKS_PER_ENTRY
        const val IMAGE_INTERVAL = 5
        const val IMAGE_COUNT = ENTRY_COUNT / IMAGE_INTERVAL
        const val SUMMARY_COUNT = 500
        const val PAGE_SIZE = 30
        const val TABLE_TOKEN = "stage9-table-token"
        const val CAPTION_TOKEN = "stage9-caption-token"
        const val METRICS_TAG = "Stage9Metrics"
        const val TEST_IMAGE_EDGE = 128
        val SCALE_START: LocalDate = LocalDate.of(2000, 1, 1)
        val SUMMARY_START: YearMonth = YearMonth.of(1500, 1)
    }
}
