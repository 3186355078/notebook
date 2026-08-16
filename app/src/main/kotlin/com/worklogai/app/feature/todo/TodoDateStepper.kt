package com.worklogai.app.feature.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TodoDateStepper(
    date: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onDateChanged(date.minusDays(1)) }) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "前一天")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (date == today) "今天 · ${date.format(dateFormatter)}" else date.format(dateFormatter),
                style = MaterialTheme.typography.titleSmall,
            )
            if (date != today) {
                TextButton(
                    onClick = { onDateChanged(today) },
                    contentPadding =
                        PaddingValues(
                            horizontal = WorkLogSpacing.small,
                            vertical = 0.dp,
                        ),
                ) {
                    Text("回到今天", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        IconButton(onClick = { onDateChanged(date.plusDays(1)) }) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "后一天")
        }
    }
}
