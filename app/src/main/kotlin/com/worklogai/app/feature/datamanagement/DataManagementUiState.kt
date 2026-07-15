package com.worklogai.app.feature.datamanagement

import com.worklogai.app.core.backup.BackupPreview
import com.worklogai.app.core.model.SummaryType

enum class DataManagementOperation {
    SELECTING_MARKDOWN_DESTINATION,
    SELECTING_BACKUP_DESTINATION,
    SELECTING_RESTORE_SOURCE,
    EXPORTING,
    CREATING_BACKUP,
    INSPECTING_BACKUP,
    RESTORING_BACKUP,
}

data class DataManagementUiState(
    val exportDate: String,
    val operation: DataManagementOperation? = null,
    val restorePreview: BackupPreview? = null,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val requiresRecovery: Boolean = false,
    val wasCancelled: Boolean = false,
) {
    val isBusy: Boolean get() = operation != null
}

sealed interface DataManagementAction {
    data class ExportDateChanged(
        val value: String,
    ) : DataManagementAction

    data object ExportEntryMarkdown : DataManagementAction

    data class ExportSummaryMarkdown(
        val type: SummaryType,
    ) : DataManagementAction

    data object CreateCompleteBackup : DataManagementAction

    data object ChooseBackupToRestore : DataManagementAction

    data object ConfirmRestore : DataManagementAction

    data object DismissRestorePreview : DataManagementAction
}

sealed interface DataManagementUiEvent {
    data class CreateMarkdownDocument(
        val suggestedFileName: String,
    ) : DataManagementUiEvent

    data class CreateBackupDocument(
        val suggestedFileName: String,
    ) : DataManagementUiEvent

    data object OpenBackupDocument : DataManagementUiEvent

    data class ShowMessage(
        val message: String,
    ) : DataManagementUiEvent

    data object RestoreCompleted : DataManagementUiEvent
}
