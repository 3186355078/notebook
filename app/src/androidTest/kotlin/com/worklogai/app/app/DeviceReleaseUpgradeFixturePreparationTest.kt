package com.worklogai.app.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceReleaseUpgradeFixturePreparationTest {
    @Test
    fun writeNonSensitive031UpgradeArchiveToExternalTestDirectory() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val original = ByteArrayOutputStream()
            assertTrue(dependencies.backupArchiveService().createBackup(original) is BackupOperationResult.Success)
            val imageDirectory = context.filesDir.resolve("attachments/images")
            val jpeg = imageDirectory.resolve(JPEG_NAME)
            val png = imageDirectory.resolve(PNG_NAME)
            val outputDirectory = requireNotNull(context.getExternalFilesDir(null)).resolve(OUTPUT_DIRECTORY)
            val output = outputDirectory.resolve(OUTPUT_NAME)

            try {
                resetAndSeed(dependencies, jpeg, png)
                assertTrue(outputDirectory.exists() || outputDirectory.mkdirs())
                output.outputStream().use { stream ->
                    assertTrue(
                        dependencies
                            .backupArchiveService()
                            .createBackup(stream) is BackupOperationResult.Success,
                    )
                }
                assertTrue(output.length() > 0)
                assertEquals(
                    4,
                    dependencies
                        .database()
                        .workEntryDao()
                        .getAllIncludingDeleted()
                        .size,
                )
                assertEquals(
                    8,
                    dependencies
                        .database()
                        .todoDao()
                        .getAll()
                        .size,
                )
                assertEquals(
                    2,
                    dependencies
                        .database()
                        .workSummaryBackupDao()
                        .getAll()
                        .size,
                )
                if (!retainFixture()) assertTrue(output.delete())
            } finally {
                assertTrue(
                    dependencies
                        .backupArchiveService()
                        .restoreBackup(ByteArrayInputStream(original.toByteArray())) is BackupOperationResult.Success,
                )
                jpeg.delete()
                png.delete()
            }
        }

    private suspend fun resetAndSeed(
        dependencies: Stage9TestEntryPoint,
        jpeg: File,
        png: File,
    ) {
        val database = dependencies.database()
        writeImage(jpeg, Bitmap.CompressFormat.JPEG, transparent = false)
        writeImage(png, Bitmap.CompressFormat.PNG, transparent = true)
        database.withTransaction {
            database.todoDao().deleteAll()
            database.attachmentDao().deleteAll()
            database.contentBlockDao().deleteAll()
            database.workEntryDao().deleteAll()
            database.workSummaryBackupDao().deleteAll()
            database.workEntryDao().insertAll(entries())
            database.contentBlockDao().insertAll(blocks())
            database.attachmentDao().insertAll(attachments(jpeg, png))
            database.workSummaryBackupDao().insertAll(summaries())
            database.todoDao().insertAll(todos())
        }
        assertTrue(
            dependencies
                .settingsRepository()
                .saveSettings(
                    AiSettings(
                        useMockProvider = true,
                        model = "stage17-upgrade-mock",
                        allowMobileNetwork = false,
                        autoWeeklySummaryEnabled = true,
                        autoMonthlySummaryEnabled = false,
                        notifyOnAutoSummaryCompletion = true,
                        autoSummaryConsentAcknowledged = true,
                    ),
                ).isSuccess,
        )
    }

    private fun writeImage(
        file: File,
        format: Bitmap.CompressFormat,
        transparent: Boolean,
    ) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (transparent) Color.TRANSPARENT else Color.rgb(42, 118, 143))
        file.outputStream().use { stream -> assertTrue(bitmap.compress(format, 90, stream)) }
        bitmap.recycle()
    }

    private fun entries(): List<WorkEntryEntity> =
        ENTRY_DATES.mapIndexed { index, date ->
            WorkEntryEntity(
                id = "stage17-entry-$index",
                entryDate = date,
                title = "Stage 17 synthetic day ${index + 1}",
                allowAiProcessing = true,
                isDeleted = false,
                createdAt = NOW.plusSeconds(index.toLong()),
                updatedAt = NOW.plusSeconds(index.toLong()),
            )
        }

    private fun blocks(): List<ContentBlockEntity> {
        val table =
            TableContent(
                title = "Stage 17 synthetic table",
                columns = listOf(TableColumn("status", "状态"), TableColumn("owner", "负责人")),
                rows = listOf(TableRow("row", mapOf("status" to "完成", "owner" to "测试用户"))),
            )
        val encoded = (KotlinxTableContentCodec().encode(table) as DataResult.Success).value
        return listOf(
            block(0, "text", ContentBlockType.TEXT, 0, text = "Stage 17 synthetic upgrade text"),
            block(0, "jpeg", ContentBlockType.IMAGE, 1),
            block(1, "text", ContentBlockType.TEXT, 0, text = "Stage 17 linked synthetic record"),
            block(1, "png", ContentBlockType.IMAGE, 1),
            block(2, "table", ContentBlockType.TABLE, 0, structured = encoded),
            block(3, "text", ContentBlockType.TEXT, 0, text = "Stage 17 month-end synthetic record"),
        )
    }

    private fun block(
        entryIndex: Int,
        suffix: String,
        type: ContentBlockType,
        order: Int,
        text: String? = null,
        structured: String? = null,
    ) = ContentBlockEntity(
        id = "stage17-entry-$entryIndex-$suffix",
        entryId = "stage17-entry-$entryIndex",
        blockType = type,
        blockOrder = order,
        textContent = text,
        structuredContent = structured,
        createdAt = NOW.plusSeconds(entryIndex.toLong()),
        updatedAt = NOW.plusSeconds(entryIndex.toLong()),
    )

    private fun attachments(
        jpeg: File,
        png: File,
    ) = listOf(
        attachment("jpeg", "stage17-entry-0-jpeg", JPEG_NAME, "image/jpeg", jpeg.length()),
        attachment("png", "stage17-entry-1-png", PNG_NAME, "image/png", png.length()),
    )

    private fun attachment(
        suffix: String,
        blockId: String,
        name: String,
        mimeType: String,
        size: Long,
    ) = AttachmentEntity(
        id = "stage17-attachment-$suffix",
        blockId = blockId,
        localPath = "images/$name",
        mimeType = mimeType,
        fileSize = size,
        width = 2,
        height = 2,
        caption = "Stage 17 synthetic $suffix caption",
        createdAt = NOW,
    )

    private fun summaries() =
        listOf(
            summary("weekly", SummaryType.WEEKLY, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)),
            summary("monthly", SummaryType.MONTHLY, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
        )

    private fun summary(
        suffix: String,
        type: SummaryType,
        start: LocalDate,
        end: LocalDate,
    ) = WorkSummaryEntity(
        id = "stage17-summary-$suffix",
        summaryType = type,
        periodStart = start,
        periodEnd = end,
        status = SummaryStatus.SUCCESS,
        sourceHash = "7".repeat(64),
        aiProvider = "mock",
        modelName = "stage17-upgrade-mock",
        originalContent = "{\"synthetic\":true}",
        editedContent = "Stage 17 synthetic edited $suffix summary",
        errorMessage = null,
        createdAt = NOW,
        updatedAt = NOW,
        generatedAt = NOW,
    )

    private fun todos(): List<TodoEntity> {
        val priorities = TodoPriority.entries
        val statuses = TodoStatus.entries
        return List(8) { index ->
            val status = statuses[index % statuses.size]
            TodoEntity(
                id = "stage17-todo-$index",
                scheduledDate = ENTRY_DATES[index % ENTRY_DATES.size],
                title = "Stage 17 synthetic todo ${index + 1}",
                note = "Non-sensitive upgrade fixture",
                priority = priorities[index % priorities.size],
                status = status,
                sortOrder = index / priorities.size,
                completionNote = if (status == TodoStatus.DONE) "Synthetic completion" else null,
                linkedContentBlockId =
                    when (index) {
                        2 -> "stage17-entry-0-text"
                        6 -> "stage17-entry-1-text"
                        else -> null
                    },
                createdAt = NOW.plusSeconds(index.toLong()),
                updatedAt = NOW.plusSeconds(index.toLong()),
                completedAt = if (status == TodoStatus.DONE) NOW.plusSeconds(index.toLong()) else null,
            )
        }
    }

    private fun retainFixture(): Boolean =
        InstrumentationRegistry
            .getArguments()
            .getString(RETAIN_FIXTURE_ARGUMENT)
            .toBoolean()

    private companion object {
        const val RETAIN_FIXTURE_ARGUMENT = "retainFixture"
        const val OUTPUT_DIRECTORY = "stage17-upgrade"
        const val OUTPUT_NAME = "worklog-ai-0.3.1-upgrade-fixture.worklog-backup.zip"
        const val JPEG_NAME = "stage17-upgrade.jpg"
        const val PNG_NAME = "stage17-upgrade.png"
        val NOW: Instant = Instant.parse("2026-08-01T08:00:00Z")
        val ENTRY_DATES =
            listOf(
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 7),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 31),
            )
    }
}
