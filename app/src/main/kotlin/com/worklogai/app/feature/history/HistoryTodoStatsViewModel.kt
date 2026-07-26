package com.worklogai.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.repository.TodoDateStats
import com.worklogai.app.core.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HistoryTodoStatsViewModel
    @Inject
    constructor(
        private val todoRepository: TodoRepository,
    ) : ViewModel() {
        private val _stats = MutableStateFlow<Map<LocalDate, TodoDateStats>>(emptyMap())
        private var loadJob: Job? = null
        private var requestedDates: Set<LocalDate> = emptySet()

        val stats = _stats.asStateFlow()

        fun load(dates: Set<LocalDate>) {
            if (dates == requestedDates) return
            requestedDates = dates
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    when (val result = todoRepository.getStatsByDates(dates)) {
                        is DataResult.Failure -> _stats.value = emptyMap()
                        is DataResult.Success -> _stats.value = result.value
                    }
                }
        }
    }
