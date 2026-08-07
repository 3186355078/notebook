package com.worklogai.app.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.SystemLocalDateProvider
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.NewWorkContent
import com.worklogai.app.core.workentry.CreateWorkContentForDateUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceHistoryBackfillIntegrationTest {
    @Test
    fun emptyDateStaysAbsentUntilContentAndBackfillSurvivesSearchBackupAndRestore() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val original = ByteArrayOutputStream()
            assertTrue(dependencies.backupArchiveService().createBackup(original) is BackupOperationResult.Success)
            val date = LocalDate.of(1904, 8, 4)
            val future = SystemLocalDateProvider().today().plusDays(1)
            val repository = dependencies.workEntryRepository()
            val createContent = CreateWorkContentForDateUseCase(repository, SystemLocalDateProvider())

            try {
                repository.getEntry(date).valueOrNull()?.let { repository.purgeEntry(it.id) }
                assertNull(repository.getEntry(date).valueOrNull())
                assertNull(repository.getEntry(date).valueOrNull())

                val text = createContent(date, NewWorkContent.Text("Stage 16 historical backfill marker"))
                assertTrue(text is DataResult.Success)
                val table =
                    createContent(
                        date,
                        NewWorkContent.Table(dependencies.tableContentEditor().createDefault()),
                    )
                assertTrue(table is DataResult.Success)
                val entry = requireNotNull(repository.getEntry(date).valueOrNull())
                assertEquals(2, entry.blocks.size)
                assertTrue(entry.blocks.first() is ContentBlock.Text)
                assertTrue(entry.blocks.last() is ContentBlock.Table)
                assertEquals(
                    listOf(date),
                    dependencies
                        .historyRepository()
                        .getEntriesInRange(date, date)
                        .valueOrNull()
                        .orEmpty()
                        .map { it.date },
                )
                assertEquals(
                    listOf(date),
                    dependencies
                        .historyRepository()
                        .searchEntries("historical backfill marker", 30, 0)
                        .valueOrNull()
                        ?.entries
                        .orEmpty()
                        .map { it.date },
                )

                assertEquals(
                    DataError.Validation(DataValidationReason.FUTURE_WORK_ENTRY),
                    (createContent(future, NewWorkContent.Text("future")) as DataResult.Failure).error,
                )
                assertNull(repository.getEntry(future).valueOrNull())

                val withBackfill = ByteArrayOutputStream()
                assertTrue(
                    dependencies.backupArchiveService().createBackup(withBackfill) is BackupOperationResult.Success,
                )
                repository.purgeEntry(entry.id)
                assertNull(repository.getEntry(date).valueOrNull())
                assertTrue(
                    dependencies
                        .backupArchiveService()
                        .restoreBackup(
                            ByteArrayInputStream(withBackfill.toByteArray()),
                        ) is BackupOperationResult.Success,
                )
                val restored = requireNotNull(repository.getEntry(date).valueOrNull())
                assertEquals(2, restored.blocks.size)
            } finally {
                assertTrue(
                    dependencies
                        .backupArchiveService()
                        .restoreBackup(ByteArrayInputStream(original.toByteArray())) is BackupOperationResult.Success,
                )
            }
        }

    private fun <T> DataResult<T>.valueOrNull(): T? = (this as? DataResult.Success)?.value
}
