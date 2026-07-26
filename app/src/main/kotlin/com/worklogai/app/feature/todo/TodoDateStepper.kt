package com.worklogai.app.feature.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.LocalDate

@Composable
internal fun TodoDateStepper(
    date: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onDateChanged(date.minusDays(1)) }) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "前一天")
        }
        Text(date.toString(), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { onDateChanged(date.plusDays(1)) }) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "后一天")
        }
    }
}
