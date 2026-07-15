package com.worklogai.app.feature.datamanagement

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.export.MarkdownDocument
import com.worklogai.app.core.export.MarkdownExportResult
import com.worklogai.app.core.export.MarkdownExportService
import com.worklogai.app.core.export.SafDocumentCoordinator
import com.worklogai.app.core.history.WorkPeriodCalculator
import com.worklogai.app.core.model.SummaryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DataManagementViewModel
    @Inject
    constructor(
        private val markdownExportService: MarkdownExportService,
        private val safDocumentCoordinator: SafDocumentCoordinator,
        private val workPeriodCalculator: WorkPeriodCalculator,
        private val timeProvider: TimeProvider,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DataManagementUiState(exportDate = timeProvider.today().toString()))
        private val _events = Channel<DataManagementUiEvent>(Channel.BUFFERED)
        private var pendingMarkdown: MarkdownDocument? = null
        private var pendingRestoreUri: Uri? = null

        val uiState = _uiState.asStateFlow()
        val events = _events.receiveAsFlow()

        fun onAction(action: DataManagementAction) {
            when (action) {
                is DataManagementAction.ExportDateChanged ->
                    updateState {
                        copy(
                            exportDate = action.value,
                            errorMessage = null,
                        )
                    }
                DataManagementAction.ExportEntryMarkdown -> exportEntry()
                is DataManagementAction.ExportSummaryMarkdown -> exportSummary(action.type)
                DataManagementAction.CreateCompleteBackup -> requestBackupDocument()
                DataManagementAction.ChooseBackupToRestore -> requestRestoreDocument()
                DataManagementAction.ConfirmRestore -> restorePreviewedBackup()
                DataManagementAction.DismissRestorePreview -> {
                    pendingRestoreUri = null
                    updateState { copy(restorePreview = null, errorMessage = null) }
                }
            }
        }

        fun onMarkdownDocumentSelected(uri: Uri?) {
            val document = pendingMarkdown ?: return
            pendingMarkdown = null
            if (uri == null) {
                _uiState.cancelled()
                return
            }
            viewModelScope.launchOperation(_uiState) {
                _uiState.start(DataManagementOperation.EXPORTING)
                val result = safDocumentCoordinator.writeMarkdown(uri, document)
                updateState { copy(operation = null) }
                if (result.isSuccess) {
                    _events.trySend(DataManagementUiEvent.ShowMessage("Markdown 已导出"))
                } else {
                    fail("导出失败，请重试")
                }
            }
        }

        fun onBackupDocumentSelected(uri: Uri?) {
            if (uri == null) {
                _uiState.cancelled()
                return
            }
            viewModelScope.launchOperation(_uiState) {
                _uiState.start(DataManagementOperation.CREATING_BACKUP)
                when (val result = safDocumentCoordinator.createBackup(uri)) {
                    is BackupOperationResult.Failure -> fail(result.message, result.requiresRecovery)
                    is BackupOperationResult.Success -> {
                        updateState { copy(operation = null) }
                        _events.trySend(
                            DataManagementUiEvent.ShowMessage(
                                if (result.value.warningCount == 0) "完整备份已创建" else "备份已创建，部分图片文件缺失",
                            ),
                        )
                    }
                }
            }
        }

        fun onBackupRestoreDocumentSelected(uri: Uri?) {
            if (uri == null) {
                _uiState.cancelled()
                return
            }
            viewModelScope.launchOperation(_uiState) {
                _uiState.start(DataManagementOperation.INSPECTING_BACKUP)
                when (val result = safDocumentCoordinator.inspectBackup(uri)) {
                    is BackupOperationResult.Failure -> fail(result.message, result.requiresRecovery)
                    is BackupOperationResult.Success -> {
                        pendingRestoreUri = uri
                        updateState { copy(operation = null, restorePreview = result.value) }
                    }
                }
            }
        }

        private fun exportEntry() {
            val date = _uiState.value.selectedDateOrNull(::fail) ?: return
            viewModelScope.launchOperation(_uiState) {
                _uiState.start(DataManagementOperation.EXPORTING)
                when (val result = markdownExportService.createEntryDocument(date)) {
                    is MarkdownExportResult.Success -> {
                        pendingMarkdown = result.document
                        updateState { copy(operation = DataManagementOperation.SELECTING_MARKDOWN_DESTINATION) }
                        _events.trySend(DataManagementUiEvent.CreateMarkdownDocument(result.document.suggestedFileName))
                    }
                    MarkdownExportResult.Empty -> fail("这一天没有可导出的工作记录")
                    MarkdownExportResult.NotFound -> fail("这一天还没有工作记录")
                    MarkdownExportResult.Failed -> fail("无法导出工作记录")
                }
            }
        }

        private fun exportSummary(type: SummaryType) {
            val date = _uiState.value.selectedDateOrNull(::fail) ?: return
            val period =
                if (type == SummaryType.WEEKLY) {
                    workPeriodCalculator.weekContaining(date)
                } else {
                    workPeriodCalculator.monthContaining(YearMonth.from(date))
                }
            viewModelScope.launchOperation(_uiState) {
                _uiState.start(DataManagementOperation.EXPORTING)
                when (val result = markdownExportService.createSummaryDocument(type, period.start, period.end)) {
                    is MarkdownExportResult.Success -> {
                        pendingMarkdown = result.document
                        updateState { copy(operation = DataManagementOperation.SELECTING_MARKDOWN_DESTINATION) }
                        _events.trySend(DataManagementUiEvent.CreateMarkdownDocument(result.document.suggestedFileName))
                    }
                    MarkdownExportResult.Empty, MarkdownExportResult.NotFound -> fail("该周期还没有可导出的总结")
                    MarkdownExportResult.Failed -> fail("无法导出工作总结")
                }
            }
        }

        private fun requestBackupDocument() {
            if (_uiState.value.isBusy) return
            _uiState.start(DataManagementOperation.SELECTING_BACKUP_DESTINATION)
            _events.trySend(DataManagementUiEvent.CreateBackupDocument("WorkLog-AI-backup.worklog-backup.zip"))
        }

        private fun requestRestoreDocument() {
            if (_uiState.value.isBusy) return
            _uiState.start(DataManagementOperation.SELECTING_RESTORE_SOURCE)
            _events.trySend(DataManagementUiEvent.OpenBackupDocument)
        }

        private fun restorePreviewedBackup() {
            val uri = pendingRestoreUri ?: return
            viewModelScope.launchOperation(_uiState) {
                _uiState.start(DataManagementOperation.RESTORING_BACKUP)
                when (val result = safDocumentCoordinator.restoreBackup(uri)) {
                    is BackupOperationResult.Failure -> fail(result.message, result.requiresRecovery)
                    is BackupOperationResult.Success -> {
                        pendingRestoreUri = null
                        updateState {
                            copy(
                                operation = null,
                                restorePreview = null,
                                warningMessage = result.warningMessage,
                            )
                        }
                        _events.trySend(
                            DataManagementUiEvent.ShowMessage(
                                result.warningMessage ?: "备份已恢复",
                            ),
                        )
                        _events.trySend(DataManagementUiEvent.RestoreCompleted)
                    }
                }
            }
        }

        private fun fail(
            message: String,
            requiresRecovery: Boolean = false,
        ) {
            updateState { copy(operation = null, errorMessage = message, requiresRecovery = requiresRecovery) }
            _events.trySend(DataManagementUiEvent.ShowMessage(message))
        }

        private val updateState: (DataManagementUiState.() -> DataManagementUiState) -> Unit = _uiState::update
    }

private fun MutableStateFlow<DataManagementUiState>.start(operation: DataManagementOperation) {
    update {
        it.copy(
            operation = operation,
            errorMessage = null,
            warningMessage = null,
            requiresRecovery = false,
            wasCancelled = false,
        )
    }
}

private fun DataManagementUiState.selectedDateOrNull(onFailure: (String, Boolean) -> Unit): LocalDate? =
    runCatching { LocalDate.parse(exportDate.trim()) }.getOrElse {
        onFailure("请输入正确的日期，例如 2026-07-14", false)
        null
    }

private fun MutableStateFlow<DataManagementUiState>.cancelled() {
    update { it.copy(operation = null, wasCancelled = true) }
}

private fun CoroutineScope.launchOperation(
    state: MutableStateFlow<DataManagementUiState>,
    block: suspend () -> Unit,
) {
    launch {
        try {
            block()
        } catch (error: CancellationException) {
            state.cancelled()
            throw error
        }
    }
}

private fun TimeProvider.today(): LocalDate = now().atZone(ZoneId.systemDefault()).toLocalDate()
