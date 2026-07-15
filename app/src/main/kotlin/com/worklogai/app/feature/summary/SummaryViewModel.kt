package com.worklogai.app.feature.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkillRequest
import com.worklogai.app.ai.usecase.GenerateWorkSummaryResult
import com.worklogai.app.app.navigation.SUMMARY_PERIOD_END_ARGUMENT
import com.worklogai.app.app.navigation.SUMMARY_PERIOD_START_ARGUMENT
import com.worklogai.app.app.navigation.SUMMARY_TYPE_ARGUMENT
import com.worklogai.app.app.navigation.summaryNavigationTargetOrNull
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.history.WorkPeriodCalculator
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel
    @Inject
    constructor(
        private val dependencies: SummaryViewModelDependencies,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val today = dependencies.timeProvider.today()
        private val initialTarget = savedStateHandle.toSummaryTargetOrNull()
        private val initialType = initialTarget?.first ?: SummaryType.WEEKLY
        private val initialPeriod = initialTarget?.second ?: dependencies.workPeriodCalculator.weekContaining(today)
        private val _uiState =
            MutableStateFlow(
                SummaryUiState(
                    summaryType = initialType,
                    period = initialPeriod,
                    selectedMonth = YearMonth.from(initialPeriod.start),
                ),
            )
        private val _events = Channel<SummaryUiEvent>(Channel.BUFFERED)
        private var loadJob: Job? = null
        private var generationJob: Job? = null

        val uiState = _uiState.asStateFlow()
        val events = _events.receiveAsFlow()

        init {
            load()
        }

        @Suppress("CyclomaticComplexMethod") // Exhaustive UI action dispatch keeps each action explicit.
        fun onAction(action: SummaryAction) {
            when (action) {
                SummaryAction.CancelEditing -> changeEditing(false)
                SummaryAction.CancelGeneration -> cancelGeneration()
                is SummaryAction.ChangeType -> changeType(action.type)
                SummaryAction.ConfirmRegenerate,
                SummaryAction.RetryGeneration,
                -> beginGeneration(confirmManualEdits = false)
                SummaryAction.ConfirmRestoreOriginal -> restoreOriginal()
                SummaryAction.CopySummary ->
                    _uiState.value.displayContent?.takeIf(String::isNotBlank)?.let { value ->
                        _events.trySend(SummaryUiEvent.CopyText(value))
                    }
                is SummaryAction.EditingTextChanged -> _uiState.value = _uiState.value.copy(editingText = action.value)
                SummaryAction.Generate -> beginGeneration(confirmManualEdits = true)
                SummaryAction.NextPeriod -> navigate(forward = true)
                SummaryAction.OpenSettings -> _events.trySend(SummaryUiEvent.OpenSettings)
                SummaryAction.PreviousPeriod -> navigate(forward = false)
                SummaryAction.RestoreOriginal -> _events.trySend(SummaryUiEvent.ConfirmRestoreOriginal)
                SummaryAction.ReturnToCurrentPeriod -> returnToCurrentPeriod()
                SummaryAction.SaveEditing -> saveEditing()
                SummaryAction.StartEditing -> changeEditing(true)
            }
        }

        private fun changeEditing(isEditing: Boolean) {
            _uiState.value =
                _uiState.value.copy(
                    isEditing = isEditing,
                    editingText =
                        _uiState.value.summary
                            ?.displayContent
                            .orEmpty(),
                )
        }

        private fun changeType(type: SummaryType) {
            if (type == _uiState.value.summaryType) return
            generationJob?.cancel()
            val period = dependencies.workPeriodCalculator.periodContaining(type, today)
            _uiState.value =
                _uiState.value.copy(
                    summaryType = type,
                    period = period,
                    selectedMonth = YearMonth.from(today),
                    isEditing = false,
                )
            load()
        }

        private fun navigate(forward: Boolean) {
            val state = _uiState.value
            val next =
                when (state.summaryType) {
                    SummaryType.WEEKLY -> state.period.start.plusWeeks(if (forward) 1 else -1)
                    SummaryType.MONTHLY -> state.selectedMonth.plusMonths(if (forward) 1 else -1).atDay(1)
                }
            if (forward && next > today) return
            _uiState.value =
                _uiState.value.copy(
                    period = dependencies.workPeriodCalculator.periodContaining(state.summaryType, next),
                    selectedMonth = YearMonth.from(next),
                    isEditing = false,
                )
            load()
        }

        private fun returnToCurrentPeriod() {
            _uiState.value =
                _uiState.value.copy(
                    period = dependencies.workPeriodCalculator.periodContaining(_uiState.value.summaryType, today),
                    selectedMonth = YearMonth.from(today),
                    isEditing = false,
                )
            load()
        }

        private fun load() {
            loadJob?.cancel()
            val type = _uiState.value.summaryType
            val period = _uiState.value.period
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            loadJob =
                viewModelScope.launch {
                    val loaded = dependencies.summaryPeriodLoader.load(type, period)
                    if (_uiState.value.summaryType != type || _uiState.value.period != period) return@launch
                    _uiState.value = _uiState.value.withLoaded(loaded)
                }
        }

        private fun beginGeneration(confirmManualEdits: Boolean) {
            if (confirmManualEdits &&
                _uiState.value.summary?.displayContent != null &&
                _uiState.value.summary.hasManualEdits(dependencies.workSummarySkill)
            ) {
                _events.trySend(SummaryUiEvent.ConfirmRegenerate)
                return
            }
            if (generationJob?.isActive == true) return
            val type = _uiState.value.summaryType
            val period = _uiState.value.period
            _uiState.value =
                _uiState.value.copy(generationState = SummaryGenerationState.Generating, errorMessage = null)
            generationJob =
                viewModelScope.launch {
                    val result = dependencies.generateWorkSummary(type, period)
                    if (_uiState.value.summaryType == type && _uiState.value.period == period) {
                        _uiState.value = _uiState.value.withGenerationResult(result)
                    }
                }
        }

        private fun cancelGeneration() {
            generationJob?.cancel()
            _uiState.value = _uiState.value.copy(generationState = SummaryGenerationState.Idle)
            _events.trySend(SummaryUiEvent.ShowMessage("已取消生成"))
        }

        private fun saveEditing() {
            val summary = _uiState.value.summary ?: return
            viewModelScope.launch {
                when (
                    dependencies.workSummaryRepository.updateEditedContent(
                        summary.summaryType,
                        summary.periodStart,
                        summary.periodEnd,
                        _uiState.value.editingText,
                    )
                ) {
                    is DataResult.Failure -> _uiState.value = _uiState.value.copy(errorMessage = "总结保存失败，请重试")
                    is DataResult.Success ->
                        _uiState.value =
                            _uiState.value.copy(
                                summary = summary.copy(editedContent = _uiState.value.editingText),
                                isEditing = false,
                            )
                }
            }
        }

        private fun restoreOriginal() {
            val summary = _uiState.value.summary ?: return
            val original = summary.parseOriginal(dependencies.workSummarySkill) ?: return
            _uiState.value = _uiState.value.copy(isEditing = true, editingText = original)
        }
    }

private fun SummaryUiState.withLoaded(loaded: SummaryPeriodLoadResult): SummaryUiState =
    when (loaded) {
        SummaryPeriodLoadResult.Failure -> copy(isLoading = false, errorMessage = "无法加载工作记录")
        is SummaryPeriodLoadResult.Success -> {
            val isOutdated =
                loaded.summary?.sourceHash != null &&
                    loaded.summary.sourceHash != loaded.currentSourceHash
            copy(
                isLoading = false,
                entryCount = loaded.entryCount,
                eligibleEntryCount = loaded.eligibleEntryCount,
                summary = loaded.summary,
                isOutdated = isOutdated,
                wasInputTruncated = false,
                generationState = loaded.summary.toGenerationState(),
            )
        }
    }

private fun SummaryUiState.withGenerationResult(result: GenerateWorkSummaryResult): SummaryUiState =
    when (result) {
        is GenerateWorkSummaryResult.Failure ->
            copy(generationState = SummaryGenerationState.Failed(result.message, result.hasPreviousContent))

        is GenerateWorkSummaryResult.NoEligibleContent ->
            copy(generationState = SummaryGenerationState.NoEligibleContent(result.allEntriesBlocked))

        is GenerateWorkSummaryResult.Skipped ->
            copy(generationState = summary.toGenerationState())

        is GenerateWorkSummaryResult.Success ->
            copy(
                summary = result.summary,
                generationState = SummaryGenerationState.Success,
                isOutdated = false,
                wasInputTruncated = result.wasInputTruncated,
            )
    }

private fun WorkSummary?.toGenerationState(): SummaryGenerationState =
    when {
        this?.status == SummaryStatus.GENERATING -> SummaryGenerationState.Generating
        this?.status == SummaryStatus.FAILED ->
            SummaryGenerationState.Failed(errorMessage ?: "总结生成失败", displayContent != null)

        this?.displayContent != null -> SummaryGenerationState.Success
        else -> SummaryGenerationState.Idle
    }

private fun WorkSummary?.hasManualEdits(workSummarySkill: WorkSummarySkill): Boolean {
    val summary = this ?: return false
    return summary.editedContent != null && summary.editedContent != summary.parseOriginal(workSummarySkill)
}

private fun WorkSummary.parseOriginal(workSummarySkill: WorkSummarySkill): String? =
    workSummarySkill
        .parse(
            originalContent.orEmpty(),
            WorkSummarySkillRequest(summaryType, periodStart, periodEnd, "zh-CN"),
        ).getOrNull()
        ?.let(workSummarySkill::format)

private fun WorkPeriodCalculator.periodContaining(
    type: SummaryType,
    date: LocalDate,
): DateRange =
    if (type == SummaryType.WEEKLY) {
        weekContaining(date)
    } else {
        monthContaining(YearMonth.from(date))
    }

private fun TimeProvider.today(): LocalDate = now().atZone(ZoneId.systemDefault()).toLocalDate()

private fun SavedStateHandle.toSummaryTargetOrNull(): Pair<SummaryType, DateRange>? =
    summaryNavigationTargetOrNull(
        get(SUMMARY_TYPE_ARGUMENT),
        get(SUMMARY_PERIOD_START_ARGUMENT),
        get(SUMMARY_PERIOD_END_ARGUMENT),
    )?.let { target -> target.summaryType to DateRange(target.periodStart, target.periodEnd) }
