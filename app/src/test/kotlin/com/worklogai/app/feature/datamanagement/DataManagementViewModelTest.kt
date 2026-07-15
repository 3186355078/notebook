package com.worklogai.app.feature.datamanagement

import android.net.Uri
import com.worklogai.app.core.backup.BackupCreationResult
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.backup.BackupPreview
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.export.MarkdownDocument
import com.worklogai.app.core.export.MarkdownExportResult
import com.worklogai.app.core.export.MarkdownExportService
import com.worklogai.app.core.export.SafDocumentCoordinator
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.feature.editor.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DataManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val markdown = FakeMarkdownExportService()
    private val saf = FakeSafDocumentCoordinator()
    private val viewModel =
        DataManagementViewModel(
            markdown,
            saf,
            DefaultWorkPeriodCalculator(),
            TimeProvider { Instant.parse("2026-07-14T08:00:00Z") },
        )

    @Test
    fun `initial state is idle and contains no transient URI`() {
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.restorePreview)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `entry export requests SAF document then writes only after a destination is selected`() {
        viewModel.onAction(DataManagementAction.ExportEntryMarkdown)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        val event = kotlinx.coroutines.runBlocking { viewModel.events.first() }
        assertEquals("工作记录_2026-07-14.md", (event as DataManagementUiEvent.CreateMarkdownDocument).suggestedFileName)
        assertEquals(0, saf.markdownWriteCount)

        viewModel.onMarkdownDocumentSelected(mockk())
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertEquals(1, saf.markdownWriteCount)
    }

    @Test
    fun `cancelled document selection is a normal no-op`() {
        viewModel.onAction(DataManagementAction.ExportEntryMarkdown)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        kotlinx.coroutines.runBlocking { viewModel.events.first() }

        viewModel.onMarkdownDocumentSelected(null)

        assertEquals(0, saf.markdownWriteCount)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.wasCancelled)
    }

    @Test
    fun `backup destination request is emitted once while selection is pending`() {
        viewModel.onAction(DataManagementAction.CreateCompleteBackup)
        viewModel.onAction(DataManagementAction.CreateCompleteBackup)

        val event = kotlinx.coroutines.runBlocking { viewModel.events.first() }
        assertTrue(event is DataManagementUiEvent.CreateBackupDocument)
        assertEquals(DataManagementOperation.SELECTING_BACKUP_DESTINATION, viewModel.uiState.value.operation)
    }

    @Test
    fun `backup SAF cancellation clears busy state without an error`() {
        viewModel.onAction(DataManagementAction.CreateCompleteBackup)
        viewModel.onBackupDocumentSelected(null)

        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.wasCancelled)
    }

    @Test
    fun `backup creation failure is presented as a controlled message`() {
        saf.backupResult = BackupOperationResult.Failure("备份创建失败")

        viewModel.onBackupDocumentSelected(mockk())
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals("备份创建失败", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `backup restore is inspected before explicit confirmation`() {
        val source = mockk<Uri>()

        viewModel.onBackupRestoreDocumentSelected(source)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(1, saf.inspectCount)
        assertEquals(
            2,
            viewModel.uiState.value.restorePreview
                ?.entryCount,
        )
        assertEquals(0, saf.restoreCount)

        viewModel.onAction(DataManagementAction.ConfirmRestore)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(1, saf.restoreCount)
        assertNull(viewModel.uiState.value.restorePreview)
    }

    @Test
    fun `restore warning is retained while completion navigation is emitted`() {
        val source = mockk<Uri>()
        saf.restoreResult = BackupOperationResult.Success(PREVIEW, "自动任务将在下次启动协调")
        viewModel.onBackupRestoreDocumentSelected(source)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        viewModel.onAction(DataManagementAction.ConfirmRestore)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals("自动任务将在下次启动协调", viewModel.uiState.value.warningMessage)
        val events =
            kotlinx.coroutines.runBlocking {
                listOf(viewModel.events.first(), viewModel.events.first())
            }
        assertTrue(events.last() is DataManagementUiEvent.RestoreCompleted)
    }

    @Test
    fun `serious rollback failure is distinct from an ordinary restore failure`() {
        val source = mockk<Uri>()
        saf.restoreResult = BackupOperationResult.Failure("恢复需要修复", requiresRecovery = true)
        viewModel.onBackupRestoreDocumentSelected(source)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        viewModel.onAction(DataManagementAction.ConfirmRestore)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.requiresRecovery)
        assertEquals("恢复需要修复", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `cancellation remains cancellation and never emits restore success`() {
        val source = mockk<Uri>()
        saf.cancelRestore = true
        viewModel.onBackupRestoreDocumentSelected(source)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        viewModel.onAction(DataManagementAction.ConfirmRestore)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.wasCancelled)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `invalid date is rejected before export`() {
        viewModel.onAction(DataManagementAction.ExportDateChanged("not-a-date"))
        viewModel.onAction(DataManagementAction.ExportSummaryMarkdown(SummaryType.MONTHLY))

        assertTrue(viewModel.uiState.value.errorMessage != null)
        assertEquals(0, markdown.summaryRequestCount)
    }

    private class FakeMarkdownExportService : MarkdownExportService {
        var summaryRequestCount = 0

        override suspend fun createEntryDocument(date: LocalDate): MarkdownExportResult =
            MarkdownExportResult.Success(MarkdownDocument("工作记录_$date.md", "# 日志"))

        override suspend fun createSummaryDocument(
            type: SummaryType,
            periodStart: LocalDate,
            periodEnd: LocalDate,
        ): MarkdownExportResult {
            summaryRequestCount++
            return MarkdownExportResult.Success(MarkdownDocument("总结.md", "# 总结"))
        }
    }

    private class FakeSafDocumentCoordinator : SafDocumentCoordinator {
        var markdownWriteCount = 0
        var inspectCount = 0
        var restoreCount = 0
        var cancelRestore = false
        var backupResult: BackupOperationResult<BackupCreationResult> =
            BackupOperationResult.Success(BackupCreationResult(0))
        var restoreResult: BackupOperationResult<BackupPreview> = BackupOperationResult.Success(PREVIEW)

        override suspend fun writeMarkdown(
            destination: Uri,
            document: MarkdownDocument,
        ): Result<Unit> {
            markdownWriteCount++
            return Result.success(Unit)
        }

        override suspend fun createBackup(destination: Uri): BackupOperationResult<BackupCreationResult> = backupResult

        override suspend fun inspectBackup(source: Uri): BackupOperationResult<BackupPreview> {
            inspectCount++
            return BackupOperationResult.Success(PREVIEW)
        }

        override suspend fun restoreBackup(source: Uri): BackupOperationResult<BackupPreview> {
            restoreCount++
            if (cancelRestore) throw CancellationException("cancelled")
            return restoreResult
        }
    }

    private companion object {
        val PREVIEW = BackupPreview(Instant.parse("2026-07-14T08:00:00Z"), 2, 3, 1, 1, 0)
    }
}
