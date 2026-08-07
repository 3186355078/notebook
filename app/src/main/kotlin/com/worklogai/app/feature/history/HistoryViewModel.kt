package com.worklogai.app.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.history.WorkEntrySummary
import com.worklogai.app.core.history.WorkHistoryPage
import com.worklogai.app.core.history.WorkHistoryRepository
import com.worklogai.app.core.history.WorkPeriodCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private const val HISTORY_PAGE_SIZE = 30
private const val SEARCH_DEBOUNCE_MILLIS = 300L

@HiltViewModel
@Suppress("TooManyFunctions")
class HistoryViewModel
    @Inject
    constructor(
        private val workHistoryRepository: WorkHistoryRepository,
        private val workPeriodCalculator: WorkPeriodCalculator,
        private val timeProvider: TimeProvider,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val initialDate =
            savedStateHandle.get<String>(SELECTED_HISTORY_DATE_KEY)?.toLocalDateOrNull() ?: currentDate()
        private val initialWeekStart = workPeriodCalculator.weekContaining(initialDate).start
        private val _uiState =
            MutableStateFlow(
                HistoryUiState(
                    mode = HistoryMode.RECENT,
                    selectedDate = initialDate,
                    selectedWeekStart = initialWeekStart,
                    selectedMonth = YearMonth.from(initialDate),
                ),
            )
        private val _events = Channel<HistoryUiEvent>(Channel.BUFFERED)
        private var loadJob: Job? = null
        private var searchJob: Job? = null
        private var requestVersion = 0L
        private var nextOffset: Int? = null

        val uiState = _uiState.asStateFlow()
        val events = _events.receiveAsFlow()

        init {
            reload(reset = true)
        }

        fun onAction(action: HistoryAction) {
            when (action) {
                HistoryAction.ClearSearch -> changeSearchQuery("")
                is HistoryAction.ChangeMode -> changeMode(action.mode)
                HistoryAction.LoadMore -> loadMore()
                HistoryAction.NextPeriod -> navigatePeriod(forward = true)
                is HistoryAction.OpenEntry -> openEntry(action.date)
                is HistoryAction.OpenBackfill -> openBackfill(action.date)
                HistoryAction.PreviousPeriod -> navigatePeriod(forward = false)
                HistoryAction.Refresh,
                HistoryAction.Retry,
                -> reload(reset = true)
                HistoryAction.ReturnToCurrentPeriod -> returnToCurrentPeriod()
                is HistoryAction.SearchQueryChanged -> changeSearchQuery(action.query)
                is HistoryAction.SelectDate -> selectDate(action.date)
            }
        }

        private fun changeMode(mode: HistoryMode) {
            if (_uiState.value.mode == mode) return
            _uiState.update { it.copy(mode = mode, errorMessage = null) }
            reload(reset = true)
        }

        private fun changeSearchQuery(query: String) {
            _uiState.update { it.copy(query = query, errorMessage = null) }
            searchJob?.cancel()
            if (query.normalizeSearchQuery().isEmpty()) {
                reload(reset = true)
            } else {
                searchJob =
                    viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE_MILLIS)
                        reload(reset = true)
                    }
            }
        }

        private fun selectDate(date: LocalDate) {
            _uiState.update {
                it.copy(
                    mode = HistoryMode.DAY,
                    selectedDate = date,
                    selectedWeekStart = workPeriodCalculator.weekContaining(date).start,
                    selectedMonth = YearMonth.from(date),
                    errorMessage = null,
                )
            }
            reload(reset = true)
        }

        private fun navigatePeriod(forward: Boolean) {
            val state = _uiState.value
            val next =
                when (state.mode) {
                    HistoryMode.RECENT -> return
                    HistoryMode.DAY -> state.selectedDate.plusDays(if (forward) 1 else -1)
                    HistoryMode.WEEK -> state.selectedWeekStart.plusWeeks(if (forward) 1 else -1)
                    HistoryMode.MONTH -> state.selectedMonth.plusMonths(if (forward) 1 else -1).atDay(1)
                }
            if (forward && next > currentDate()) return
            _uiState.update {
                when (state.mode) {
                    HistoryMode.DAY -> it.copy(selectedDate = next, errorMessage = null)
                    HistoryMode.WEEK -> it.copy(selectedWeekStart = next, errorMessage = null)
                    HistoryMode.MONTH -> it.copy(selectedMonth = YearMonth.from(next), errorMessage = null)
                    HistoryMode.RECENT -> it
                }
            }
            reload(reset = true)
        }

        private fun returnToCurrentPeriod() {
            if (_uiState.value.mode == HistoryMode.RECENT) return
            val today = currentDate()
            val currentWeekStart = workPeriodCalculator.weekContaining(today).start
            _uiState.update {
                when (it.mode) {
                    HistoryMode.DAY -> it.copy(selectedDate = today, errorMessage = null)
                    HistoryMode.WEEK -> it.copy(selectedWeekStart = currentWeekStart, errorMessage = null)
                    HistoryMode.MONTH -> it.copy(selectedMonth = YearMonth.from(today), errorMessage = null)
                    HistoryMode.RECENT -> it
                }
            }
            reload(reset = true)
        }

        private fun loadMore() {
            if (_uiState.value.isLoadingMore || _uiState.value.isLoading || nextOffset == null) return
            reload(reset = false)
        }

        private fun reload(reset: Boolean) {
            loadJob?.cancel()
            val state = _uiState.value
            val offset = if (reset) 0 else nextOffset ?: return
            val version = ++requestVersion
            _uiState.update {
                it.copy(
                    isLoading = reset,
                    isLoadingMore = !reset,
                    isSearching = state.query.normalizeSearchQuery().isNotEmpty(),
                    errorMessage = null,
                )
            }
            loadJob =
                viewModelScope.launch {
                    val result = queryHistory(workHistoryRepository, workPeriodCalculator, state, offset)
                    if (version != requestVersion) return@launch
                    applyResult(result, reset, state.query.normalizeSearchQuery().isNotEmpty())
                }
        }

        private fun applyResult(
            result: DataResult<QueryResult>,
            reset: Boolean,
            isSearching: Boolean,
        ) {
            when (result) {
                is DataResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            isSearching = false,
                            errorMessage = errorMessageFor(it.mode, isSearching, reset),
                        )
                    }
                }

                is DataResult.Success -> {
                    nextOffset = result.value.nextOffset
                    _uiState.update { state ->
                        val combined =
                            if (reset) {
                                result.value.items
                            } else {
                                (state.items + result.value.items).distinctBy(HistoryItemUiModel::entryId)
                            }
                        state.copy(
                            items = combined,
                            isLoading = false,
                            isSearching = false,
                            isLoadingMore = false,
                            canLoadMore = nextOffset != null,
                            errorMessage = null,
                        )
                    }
                }
            }
        }

        private fun openEntry(date: LocalDate) {
            _events.trySend(HistoryUiEvent.OpenEditor(date))
        }

        private fun openBackfill(date: LocalDate) {
            if (date > currentDate()) {
                showFutureDateMessage()
                return
            }
            _events.trySend(HistoryUiEvent.OpenEditor(date))
        }

        private fun showFutureDateMessage() {
            _events.trySend(HistoryUiEvent.ShowMessage("只能补充今天或更早的工作记录"))
        }

        private fun currentDate(): LocalDate = timeProvider.today()
    }

private data class QueryResult(
    val items: List<HistoryItemUiModel>,
    val nextOffset: Int?,
)

private suspend fun queryHistory(
    repository: WorkHistoryRepository,
    periodCalculator: WorkPeriodCalculator,
    state: HistoryUiState,
    offset: Int,
): DataResult<QueryResult> {
    val query = state.query.normalizeSearchQuery()
    if (query.isNotEmpty()) {
        return repository.searchEntries(query, HISTORY_PAGE_SIZE, offset).toQueryResult()
    }
    return when (state.mode) {
        HistoryMode.RECENT -> repository.getRecentEntries(HISTORY_PAGE_SIZE, offset).toQueryResult()
        HistoryMode.DAY -> rangeQuery(repository, periodCalculator.dayContaining(state.selectedDate))
        HistoryMode.WEEK -> rangeQuery(repository, periodCalculator.weekContaining(state.selectedWeekStart))
        HistoryMode.MONTH -> rangeQuery(repository, periodCalculator.monthContaining(state.selectedMonth))
    }
}

private suspend fun rangeQuery(
    repository: WorkHistoryRepository,
    range: com.worklogai.app.core.history.DateRange,
): DataResult<QueryResult> =
    when (val result = repository.getEntriesInRange(range.start, range.end)) {
        is DataResult.Failure -> result
        is DataResult.Success -> DataResult.Success(QueryResult(result.value.map(WorkEntrySummary::toUiModel), null))
    }

private fun DataResult<WorkHistoryPage>.toQueryResult(): DataResult<QueryResult> =
    when (this) {
        is DataResult.Failure -> this
        is DataResult.Success ->
            DataResult.Success(QueryResult(value.entries.map(WorkEntrySummary::toUiModel), value.nextOffset))
    }

private fun WorkEntrySummary.toUiModel(): HistoryItemUiModel =
    HistoryItemUiModel(
        entryId = entryId,
        date = date,
        dateLabel = date.format(HISTORY_DATE_FORMATTER),
        weekdayLabel = DAY_OF_WEEK_LABELS.getValue(date.dayOfWeek),
        previewText = previewText,
        contentSummary = contentSummary,
    )

private fun String.normalizeSearchQuery(): String = trim().replace(SEARCH_QUERY_WHITESPACE, " ")

private fun String.toLocalDateOrNull(): LocalDate? = runCatching(LocalDate::parse).getOrNull()

private fun TimeProvider.today(): LocalDate = now().atZone(ZoneId.systemDefault()).toLocalDate()

private fun errorMessageFor(
    mode: HistoryMode,
    isSearching: Boolean,
    reset: Boolean,
): String =
    when {
        isSearching -> "搜索失败，请重试"
        !reset -> "更多记录加载失败"
        mode == HistoryMode.DAY -> "无法加载这一天的记录"
        mode == HistoryMode.WEEK -> "无法加载这一周的记录"
        mode == HistoryMode.MONTH -> "无法加载这个月的记录"
        else -> "历史记录加载失败"
    }

private const val SELECTED_HISTORY_DATE_KEY = "history.selected_date"

private val SEARCH_QUERY_WHITESPACE = Regex("\\s+")

private val HISTORY_DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)

private val DAY_OF_WEEK_LABELS =
    mapOf(
        DayOfWeek.MONDAY to "周一",
        DayOfWeek.TUESDAY to "周二",
        DayOfWeek.WEDNESDAY to "周三",
        DayOfWeek.THURSDAY to "周四",
        DayOfWeek.FRIDAY to "周五",
        DayOfWeek.SATURDAY to "周六",
        DayOfWeek.SUNDAY to "周日",
    )
