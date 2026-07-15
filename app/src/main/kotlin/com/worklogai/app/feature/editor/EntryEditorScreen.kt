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
    modifier: Modifier = Modifier,
) {
    val date = entryDate?.let(::parseDateOrNull)
    if (date == null || date > LocalDate.now()) {
        LaunchedEffect(entryDate) { onInvalidDate() }
        EmptyState(
            title = "无法打开该日期",
            body = "请返回历史记录重新选择。",
            modifier = modifier,
        )
    } else {
        TodayScreen(followCurrentDate = false, modifier = modifier)
    }
}

private fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
