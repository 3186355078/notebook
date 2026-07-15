package com.worklogai.app.feature.history

import java.time.LocalDate
import java.time.YearMonth

enum class HistoryMode {
    RECENT,
    DAY,
    WEEK,
    MONTH,
}

data class HistoryItemUiModel(
    val entryId: String,
    val date: LocalDate,
    val dateLabel: String,
    val weekdayLabel: String,
    val previewText: String,
    val contentSummary: String,
)

data class HistoryUiState(
    val mode: HistoryMode,
    val selectedDate: LocalDate,
    val selectedWeekStart: LocalDate,
    val selectedMonth: YearMonth,
    val query: String = "",
    val items: List<HistoryItemUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface HistoryAction {
    data object Retry : HistoryAction

    data object Refresh : HistoryAction

    data object LoadMore : HistoryAction

    data class ChangeMode(
        val mode: HistoryMode,
    ) : HistoryAction

    data class SearchQueryChanged(
        val query: String,
    ) : HistoryAction

    data object ClearSearch : HistoryAction

    data object PreviousPeriod : HistoryAction

    data object NextPeriod : HistoryAction

    data object ReturnToCurrentPeriod : HistoryAction

    data class SelectDate(
        val date: LocalDate,
    ) : HistoryAction

    data class OpenEntry(
        val date: LocalDate,
    ) : HistoryAction
}

sealed interface HistoryUiEvent {
    data class OpenEditor(
        val date: LocalDate,
    ) : HistoryUiEvent

    data class ShowMessage(
        val message: String,
    ) : HistoryUiEvent
}
