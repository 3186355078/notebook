package com.worklogai.app.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceMarkdownSafFixtureTest {
    @Test
    fun manageMonthlySummaryFixture() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val snapshot = context.filesDir.resolve(SNAPSHOT_NAME)

            when (InstrumentationRegistry.getArguments().getString(MODE_ARGUMENT).orEmpty()) {
                PREPARE_MODE -> {
                    snapshot.outputStream().use { output ->
                        assertTrue(
                            dependencies.backupArchiveService().createBackup(output) is BackupOperationResult.Success,
                        )
                    }
                    dependencies
                        .database()
                        .workSummaryDao()
                        .getByPeriod(
                            SummaryType.MONTHLY,
                            PERIOD_START,
                            PERIOD_END,
                        )?.let { dependencies.database().workSummaryDao().delete(it) }
                    dependencies.database().workSummaryDao().insert(
                        WorkSummaryEntity(
                            id = SUMMARY_ID,
                            summaryType = SummaryType.MONTHLY,
                            periodStart = PERIOD_START,
                            periodEnd = PERIOD_END,
                            status = SummaryStatus.SUCCESS,
                            sourceHash = "8".repeat(64),
                            aiProvider = "mock",
                            modelName = "mock-work-summary-v1",
                            originalContent = "{\"overview\":\"synthetic original marker\"}",
                            editedContent = EDITED_MARKER,
                            errorMessage = null,
                            createdAt = NOW,
                            updatedAt = NOW,
                            generatedAt = NOW,
                        ),
                    )
                    assertTrue(snapshot.isFile)
                }

                CLEANUP_MODE -> {
                    assertTrue(snapshot.isFile)
                    snapshot.inputStream().use { input ->
                        assertTrue(
                            dependencies.backupArchiveService().restoreBackup(input) is BackupOperationResult.Success,
                        )
                    }
                    assertTrue(snapshot.delete())
                    assertFalse(snapshot.exists())
                }
            }
        }

    private companion object {
        const val MODE_ARGUMENT = "markdownFixture"
        const val PREPARE_MODE = "prepare"
        const val CLEANUP_MODE = "cleanup"
        const val SNAPSHOT_NAME = "stage9-markdown-saf-original.worklog-backup.zip"
        const val SUMMARY_ID = "stage9-markdown-saf-monthly-summary"
        const val EDITED_MARKER = "Stage 9 monthly edited export marker"
        val PERIOD_START: LocalDate = LocalDate.of(2026, 6, 1)
        val PERIOD_END: LocalDate = LocalDate.of(2026, 6, 30)
        val NOW: Instant = Instant.parse("2026-07-18T00:00:00Z")
    }
}
