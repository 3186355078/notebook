package com.worklogai.app.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryBackfillDatePicker(
    initialDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialSelection = if (initialDate <= today) initialDate else today
    val selectableDates =
        remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    isBackfillDateAllowed(backfillDateFromUtcMillis(utcTimeMillis), today)

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        }
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialSelection.toUtcMillis(),
            selectableDates = selectableDates,
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val selected = pickerState.selectedDateMillis?.let(::backfillDateFromUtcMillis)
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected?.let { isBackfillDateAllowed(it, today) } == true,
                modifier = Modifier.testTag("history_backfill_confirm"),
            ) {
                Text("打开")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        modifier = Modifier.testTag("history_backfill_date_picker"),
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Column(modifier = Modifier.padding(horizontal = WorkLogSpacing.largePlus)) {
                    Text("选择补录日期", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "选择需要补充工作记录的日期",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
    }
}

internal fun isBackfillDateAllowed(
    date: LocalDate,
    today: LocalDate,
): Boolean = date <= today

internal fun backfillDateFromUtcMillis(utcTimeMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
