package com.worklogai.app.app

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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class DeviceSafBackupFixturePreparationTest {
    @Test
    fun writeNonSensitiveBaseArchiveToExternalTestDirectory() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val original = ByteArrayOutputStream()
            assertTrue(dependencies.backupArchiveService().createBackup(original) is BackupOperationResult.Success)
            val image = context.filesDir.resolve("attachments/images/$IMAGE_NAME")

            try {
                seedFixture(dependencies, image)
                val directory = requireNotNull(context.getExternalFilesDir(null)).resolve("stage9-saf-fixtures")
                assertTrue(directory.exists() || directory.mkdirs())
                val output = directory.resolve(BASE_ARCHIVE_NAME)
                output.outputStream().use { stream ->
                    assertTrue(
                        dependencies.backupArchiveService().createBackup(stream) is BackupOperationResult.Success,
                    )
                }
                assertTrue(output.length() > 0)
                if (!retainFixture()) {
                    assertTrue(output.delete())
                }
            } finally {
                assertTrue(
                    dependencies.backupArchiveService().restoreBackup(
                        ByteArrayInputStream(original.toByteArray()),
                    ) is BackupOperationResult.Success,
                )
                image.delete()
            }
        }

    private fun retainFixture(): Boolean =
        InstrumentationRegistry
            .getArguments()
            .getString(RETAIN_FIXTURE_ARGUMENT)
            .toBoolean()

    private suspend fun seedFixture(
        dependencies: Stage9TestEntryPoint,
        image: java.io.File,
    ) {
        dependencies.database().workEntryDao().getWithContentByDateIncludingDeleted(ENTRY_DATE)?.entry?.let {
            dependencies.database().workEntryDao().delete(it)
        }
        dependencies.database().workSummaryDao().getByPeriod(SummaryType.WEEKLY, WEEK_START, WEEK_END)?.let {
            dependencies.database().workSummaryDao().delete(it)
        }
        val entry =
            WorkEntryEntity(
                id = ENTRY_ID,
                entryDate = ENTRY_DATE,
                title = "Stage 9 SAF synthetic fixture",
                allowAiProcessing = true,
                isDeleted = false,
                createdAt = NOW,
                updatedAt = NOW,
            )
        dependencies.database().workEntryDao().insert(entry)
        val table =
            TableContent(
                title = "Stage 9 table",
                columns = listOf(TableColumn("column", "Column")),
                rows = listOf(TableRow("row", mapOf("column" to "Value"))),
            )
        val encoded = (KotlinxTableContentCodec().encode(table) as DataResult.Success).value
        dependencies.database().contentBlockDao().insertAll(
            listOf(
                block("text", ContentBlockType.TEXT, 0, "Synthetic text", null),
                block("image", ContentBlockType.IMAGE, 1, null, null),
                block("table", ContentBlockType.TABLE, 2, null, encoded),
            ),
        )
        image.parentFile?.mkdirs()
        image.writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG))
        dependencies.database().attachmentDao().insert(
            AttachmentEntity(
                id = ATTACHMENT_ID,
                blockId = "$ENTRY_ID-image",
                localPath = "images/$IMAGE_NAME",
                mimeType = "image/png",
                fileSize = image.length(),
                width = 1,
                height = 1,
                caption = "Synthetic caption",
                createdAt = NOW,
            ),
        )
        dependencies.database().workSummaryDao().insert(
            WorkSummaryEntity(
                id = SUMMARY_ID,
                summaryType = SummaryType.WEEKLY,
                periodStart = WEEK_START,
                periodEnd = WEEK_END,
                status = SummaryStatus.SUCCESS,
                sourceHash = "9".repeat(64),
                aiProvider = "mock",
                modelName = "mock",
                originalContent = "{}",
                editedContent = "Synthetic summary",
                errorMessage = null,
                createdAt = NOW,
                updatedAt = NOW,
                generatedAt = NOW,
            ),
        )
    }

    private fun block(
        suffix: String,
        type: ContentBlockType,
        order: Int,
        text: String?,
        structured: String?,
    ) = ContentBlockEntity(
        id = "$ENTRY_ID-$suffix",
        entryId = ENTRY_ID,
        blockType = type,
        blockOrder = order,
        textContent = text,
        structuredContent = structured,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        const val BASE_ARCHIVE_NAME = "stage9-base.worklog-backup.zip"
        const val RETAIN_FIXTURE_ARGUMENT = "retainFixture"
        const val ENTRY_ID = "stage9-saf-entry"
        const val ATTACHMENT_ID = "stage9-saf-attachment"
        const val SUMMARY_ID = "stage9-saf-summary"
        const val IMAGE_NAME = "stage9-saf.png"
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+Xxw0WQAAAABJRU5ErkJggg=="
        val ENTRY_DATE: LocalDate = LocalDate.of(1901, 1, 7)
        val WEEK_START: LocalDate = LocalDate.of(1901, 1, 7)
        val WEEK_END: LocalDate = LocalDate.of(1901, 1, 13)
        val NOW: Instant = Instant.parse("2026-07-13T00:00:00Z")
    }
}
