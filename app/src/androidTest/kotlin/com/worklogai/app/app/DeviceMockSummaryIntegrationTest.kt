package com.worklogai.app.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.ai.usecase.GenerateWorkSummaryResult
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.feature.summary.SummaryPeriodLoadResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceMockSummaryIntegrationTest {
    @Test
    fun completedWeekAndMonthGeneratePersistEditAndDetectSourceChangesWithMockProvider() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val backup = ByteArrayOutputStream()
            assertTrue(dependencies.backupArchiveService().createBackup(backup) is BackupOperationResult.Success)
            val originalSettings = dependencies.settingsRepository().getSettings()

            try {
                assertTrue(
                    dependencies
                        .settingsRepository()
                        .saveSettings(AiSettings(useMockProvider = true, model = "mock-work-summary-v1"))
                        .isSuccess,
                )
                replacePeriodData(dependencies, WEEKLY_PERIOD, "weekly")
                replacePeriodData(dependencies, MONTHLY_PERIOD, "monthly")

                val weekly = dependencies.generateWorkSummary()(SummaryType.WEEKLY, WEEKLY_PERIOD)
                val monthly = dependencies.generateWorkSummary()(SummaryType.MONTHLY, MONTHLY_PERIOD)

                assertTrue(weekly is GenerateWorkSummaryResult.Success)
                assertTrue(monthly is GenerateWorkSummaryResult.Success)
                val weeklySummary = (weekly as GenerateWorkSummaryResult.Success).summary
                val monthlySummary = (monthly as GenerateWorkSummaryResult.Success).summary
                assertEquals(SummaryStatus.SUCCESS, weeklySummary.status)
                assertEquals(SummaryStatus.SUCCESS, monthlySummary.status)
                assertEquals("mock", weeklySummary.aiProvider)
                assertEquals("mock", monthlySummary.aiProvider)
                assertFalse(weeklySummary.originalContent.orEmpty().contains(BLOCKED_MARKER))
                assertFalse(monthlySummary.originalContent.orEmpty().contains(BLOCKED_MARKER))
                assertTrue(weeklySummary.editedContent.orEmpty().isNotBlank())
                assertTrue(monthlySummary.editedContent.orEmpty().isNotBlank())

                val manualEdit = "Stage 9 device-edited weekly summary"
                assertTrue(
                    dependencies
                        .summaryRepository()
                        .updateEditedContent(SummaryType.WEEKLY, WEEKLY_PERIOD.start, WEEKLY_PERIOD.end, manualEdit) is
                        DataResult.Success,
                )
                val reloaded =
                    dependencies
                        .summaryRepository()
                        .getSummary(SummaryType.WEEKLY, WEEKLY_PERIOD.start, WEEKLY_PERIOD.end)
                assertEquals(manualEdit, (reloaded as DataResult.Success).value?.editedContent)

                val textBlock = dependencies.database().contentBlockDao().getById("stage9-weekly-text-0")
                assertNotNull(textBlock)
                dependencies
                    .database()
                    .contentBlockDao()
                    .update(
                        requireNotNull(
                            textBlock,
                        ).copy(textContent = "Stage 9 changed source", updatedAt = NOW.plusSeconds(1)),
                    )
                val loaded = dependencies.summaryPeriodLoader().load(SummaryType.WEEKLY, WEEKLY_PERIOD)
                assertTrue(loaded is SummaryPeriodLoadResult.Success)
                val success = loaded as SummaryPeriodLoadResult.Success
                assertNotEquals(success.summary?.sourceHash, success.currentSourceHash)
            } finally {
                val restored =
                    dependencies
                        .backupArchiveService()
                        .restoreBackup(ByteArrayInputStream(backup.toByteArray()))
                assertTrue(restored is BackupOperationResult.Success)
                assertTrue(dependencies.settingsRepository().saveSettings(originalSettings).isSuccess)
            }
        }

    private suspend fun replacePeriodData(
        dependencies: Stage9TestEntryPoint,
        period: DateRange,
        prefix: String,
    ) {
        val database = dependencies.database()
        val existingSummary =
            database.workSummaryDao().getByPeriod(
                if (prefix == "weekly") SummaryType.WEEKLY else SummaryType.MONTHLY,
                period.start,
                period.end,
            )
        if (existingSummary != null) database.workSummaryDao().delete(existingSummary)
        val dates = listOf(period.start, period.start.plusDays(1), period.end.minusDays(1), period.end)
        dates.forEach { date ->
            val existingEntry = database.workEntryDao().getWithContentByDateIncludingDeleted(date)?.entry
            if (existingEntry != null) database.workEntryDao().delete(existingEntry)
        }
        dates.forEachIndexed { index, date ->
            database.workEntryDao().insert(
                WorkEntryEntity(
                    id = "stage9-$prefix-entry-$index",
                    entryDate = date,
                    title = "Stage 9 $prefix synthetic record $index",
                    allowAiProcessing = index != dates.lastIndex,
                    isDeleted = false,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            )
            when (index) {
                0 -> insertText(dependencies, prefix, index)
                1 -> insertImage(dependencies, prefix, index)
                2 -> insertTable(dependencies, prefix, index)
                else -> insertBlockedText(dependencies, prefix, index)
            }
        }
    }

    private suspend fun insertText(
        dependencies: Stage9TestEntryPoint,
        prefix: String,
        index: Int,
    ) {
        dependencies.database().contentBlockDao().insert(
            block(prefix, index, ContentBlockType.TEXT, text = "Stage 9 completed item and next action"),
        )
    }

    private suspend fun insertImage(
        dependencies: Stage9TestEntryPoint,
        prefix: String,
        index: Int,
    ) {
        val block = block(prefix, index, ContentBlockType.IMAGE)
        dependencies.database().contentBlockDao().insert(block)
        dependencies.database().attachmentDao().insert(
            AttachmentEntity(
                id = "${block.id}-attachment",
                blockId = block.id,
                localPath = "images/stage9-$prefix-missing.png",
                mimeType = "image/png",
                fileSize = 1,
                width = 1,
                height = 1,
                caption = "Stage 9 image note about a resolved problem",
                createdAt = NOW,
            ),
        )
    }

    private suspend fun insertTable(
        dependencies: Stage9TestEntryPoint,
        prefix: String,
        index: Int,
    ) {
        val table =
            TableContent(
                title = "Stage 9 plan table",
                columns = listOf(TableColumn("status", "Status")),
                rows = listOf(TableRow("row", mapOf("status" to "In progress"))),
            )
        val encoded = KotlinxTableContentCodec().encode(table) as DataResult.Success
        dependencies.database().contentBlockDao().insert(
            block(prefix, index, ContentBlockType.TABLE, structured = encoded.value),
        )
    }

    private suspend fun insertBlockedText(
        dependencies: Stage9TestEntryPoint,
        prefix: String,
        index: Int,
    ) {
        dependencies.database().contentBlockDao().insert(
            block(prefix, index, ContentBlockType.TEXT, text = BLOCKED_MARKER),
        )
    }

    private fun block(
        prefix: String,
        index: Int,
        type: ContentBlockType,
        text: String? = null,
        structured: String? = null,
    ) = ContentBlockEntity(
        id = "stage9-$prefix-${type.name.lowercase()}-$index",
        entryId = "stage9-$prefix-entry-$index",
        blockType = type,
        blockOrder = 0,
        textContent = text,
        structuredContent = structured,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-01T00:00:00Z")
        val WEEKLY_PERIOD = DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12))
        val MONTHLY_PERIOD = DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))
        const val BLOCKED_MARKER = "STAGE9_BLOCKED_CONTENT_MUST_NOT_APPEAR"
    }
}
