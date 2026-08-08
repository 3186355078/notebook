package com.worklogai.app.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.worklogai.app.core.designsystem.component.EmptyState
import java.time.LocalDate

/** Fixed-date editor host used by History. It intentionally never follows the next midnight. */
@Composable
fun EntryEditorScreen(
    entryDate: String?,
    onInvalidDate: () -> Unit,
    onOpenEntry: (LocalDate) -> Unit = {},
    onOpenLinkedEntry: (LocalDate, String) -> Boolean = { date, _ ->
        onOpenEntry(date)
        true
    },
    modifier: Modifier = Modifier,
) {
    val date = entryDate?.let(::parseDateOrNull)
    if (date == null) {
        LaunchedEffect(entryDate) { onInvalidDate() }
        EmptyState(
            title = "无法打开该日期",
            body = "请选择有效日期。",
            modifier = modifier,
        )
    } else {
        TodayScreen(
            followCurrentDate = false,
            navigation =
                TodayScreenNavigation(
                    openEntry = onOpenEntry,
                    openLinkedEntry = onOpenLinkedEntry,
                ),
            modifier = modifier,
        )
    }
}

private fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
