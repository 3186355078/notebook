package com.worklogai.app.feature.summary

import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import java.time.YearMonth

sealed interface SummaryGenerationState {
    data object Idle : SummaryGenerationState

    data object Generating : SummaryGenerationState

    data object Success : SummaryGenerationState

    data class Failed(
        val message: String,
        val hasPreviousContent: Boolean,
    ) : SummaryGenerationState

    data class NoEligibleContent(
        val allEntriesBlocked: Boolean,
    ) : SummaryGenerationState
}

data class SummaryUiState(
    val summaryType: SummaryType,
    val period: DateRange,
    val selectedMonth: YearMonth,
    val isLoading: Boolean = true,
    val entryCount: Int = 0,
    val eligibleEntryCount: Int = 0,
    val summary: WorkSummary? = null,
    val generationState: SummaryGenerationState = SummaryGenerationState.Idle,
    val isOutdated: Boolean = false,
    val wasInputTruncated: Boolean = false,
    val isEditing: Boolean = false,
    val editingText: String = "",
    val errorMessage: String? = null,
) {
    val displayContent: String? get() = if (isEditing) editingText else summary?.displayContent
    val hasManualEdits: Boolean get() = summary?.editedContent != null && summary.editedContent != editingText
}

sealed interface SummaryAction {
    data class ChangeType(
        val type: SummaryType,
    ) : SummaryAction

    data object PreviousPeriod : SummaryAction

    data object NextPeriod : SummaryAction

    data object ReturnToCurrentPeriod : SummaryAction

    data object Generate : SummaryAction

    data object ConfirmRegenerate : SummaryAction

    data object CancelGeneration : SummaryAction

    data object RetryGeneration : SummaryAction

    data object StartEditing : SummaryAction

    data class EditingTextChanged(
        val value: String,
    ) : SummaryAction

    data object SaveEditing : SummaryAction

    data object CancelEditing : SummaryAction

    data object RestoreOriginal : SummaryAction

    data object ConfirmRestoreOriginal : SummaryAction

    data object CopySummary : SummaryAction

    data object OpenSettings : SummaryAction
}

sealed interface SummaryUiEvent {
    data object OpenSettings : SummaryUiEvent

    data class CopyText(
        val value: String,
    ) : SummaryUiEvent

    data class ShowMessage(
        val message: String,
    ) : SummaryUiEvent

    data object ConfirmRegenerate : SummaryUiEvent

    data object ConfirmRestoreOriginal : SummaryUiEvent
}
