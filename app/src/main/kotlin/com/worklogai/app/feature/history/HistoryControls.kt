package com.worklogai.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.designsystem.component.workLogFilledInputColors
import com.worklogai.app.core.designsystem.theme.WorkLogIndicatorSize
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HistorySearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkLogSpacing.largePlus, vertical = WorkLogSpacing.small),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        placeholder = { Text("搜索工作记录") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "搜索历史记录") },
        trailingIcon = {
            when {
                isSearching ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(WorkLogIndicatorSize.inline),
                        strokeWidth = 2.dp,
                    )
                query.isNotEmpty() ->
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Clear, contentDescription = "清除搜索")
                    }
            }
        },
        colors = workLogFilledInputColors(),
    )
}

@Composable
internal fun HistoryModeSelector(
    selectedMode: HistoryMode,
    onModeSelected: (HistoryMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkLogSpacing.largePlus),
    ) {
        HistoryMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, HistoryMode.entries.size),
                label = { Text(mode.label) },
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WorkLogSpacing.largePlus,
                    vertical = WorkLogSpacing.small,
                ),
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
                TextButton(onClick = { onAction(HistoryAction.ReturnToCurrentPeriod) }) {
                    Text(state.returnLabel)
                }
            }
        }
        IconButton(onClick = { onAction(HistoryAction.NextPeriod) }, enabled = canMoveNext) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "下一个日期范围")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePickerButton(
    selectedDate: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    TextButton(onClick = { pickerVisible = true }) {
        Icon(
            Icons.Outlined.CalendarMonth,
            contentDescription = "选择日期",
            modifier = Modifier.size(16.dp),
        )
        Text("选择日期", modifier = Modifier.padding(start = WorkLogSpacing.extraSmall))
    }
    if (pickerVisible) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerVisible = false
                        pickerState.selectedDateMillis
                            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                            ?.let(onSelected)
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerVisible = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
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
