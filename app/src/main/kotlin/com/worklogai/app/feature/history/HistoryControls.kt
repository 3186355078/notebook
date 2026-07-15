package com.worklogai.app.feature.history

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HistorySearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        singleLine = true,
        label = { Text("搜索工作记录") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "搜索历史记录") },
        trailingIcon = {
            when {
                isSearching -> CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                query.isNotEmpty() ->
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Clear, contentDescription = "清除搜索")
                    }
            }
        },
    )
}

@Composable
internal fun HistoryModeSelector(
    selectedMode: HistoryMode,
    onModeSelected: (HistoryMode) -> Unit,
) {
    TabRow(selectedTabIndex = HistoryMode.entries.indexOf(selectedMode)) {
        HistoryMode.entries.forEach { mode ->
            Tab(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                text = { Text(mode.label) },
            )
        }
    }
}

@Composable
internal fun HistoryPeriodNavigator(
    state: HistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    if (state.mode == HistoryMode.RECENT || state.query.isNotBlank()) return
    val today = LocalDate.now()
    val canMoveNext = state.canMoveToNextPeriod(today)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onAction(HistoryAction.PreviousPeriod) }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "上一个日期范围")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.periodLabel, style = MaterialTheme.typography.titleSmall)
            if (state.mode == HistoryMode.DAY) {
                HistoryDatePickerButton(
                    selectedDate = state.selectedDate,
                    onSelected = { onAction(HistoryAction.SelectDate(it)) },
                )
            } else {
                OutlinedButton(onClick = { onAction(HistoryAction.ReturnToCurrentPeriod) }) {
                    Text(state.returnLabel)
                }
            }
        }
        IconButton(onClick = { onAction(HistoryAction.NextPeriod) }, enabled = canMoveNext) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "下一个日期范围")
        }
    }
}

@Composable
private fun HistoryDatePickerButton(
    selectedDate: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth,
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
        },
    ) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = "选择日期")
        Text("选择日期", modifier = Modifier.padding(start = 6.dp))
    }
}

private val HistoryMode.label: String
    get() =
        when (this) {
            HistoryMode.RECENT -> "最近"
            HistoryMode.DAY -> "按日"
            HistoryMode.WEEK -> "按周"
            HistoryMode.MONTH -> "按月"
        }

private val HistoryUiState.periodLabel: String
    get() =
        when (mode) {
            HistoryMode.RECENT -> ""
            HistoryMode.DAY -> selectedDate.format(DAY_FORMATTER)
            HistoryMode.WEEK -> selectedWeekStart.toRangeLabel(selectedWeekStart.plusDays(DAYS_PER_WEEK_MINUS_ONE))
            HistoryMode.MONTH -> selectedMonth.format(MONTH_FORMATTER)
        }

private val HistoryUiState.returnLabel: String
    get() =
        when (mode) {
            HistoryMode.WEEK -> "回到本周"
            HistoryMode.MONTH -> "回到本月"
            else -> "回到今天"
        }

private fun HistoryUiState.canMoveToNextPeriod(today: LocalDate): Boolean =
    when (mode) {
        HistoryMode.DAY -> selectedDate < today
        HistoryMode.WEEK -> selectedWeekStart < today.startOfWeek()
        HistoryMode.MONTH -> selectedMonth < YearMonth.from(today)
        HistoryMode.RECENT -> false
    }

private fun LocalDate.startOfWeek(): LocalDate = minusDays(dayOfWeek.value.toLong() - 1L)

private fun LocalDate.toRangeLabel(end: LocalDate): String =
    if (year == end.year && month == end.month) {
        format(DATE_FORMATTER) + "—" + end.format(DATE_FORMATTER)
    } else {
        format(YEAR_DATE_FORMATTER) + "—" + end.format(YEAR_DATE_FORMATTER)
    }

private const val DAYS_PER_WEEK_MINUS_ONE = 6L

private val DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)
private val YEAR_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)
private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月", Locale.SIMPLIFIED_CHINESE)
