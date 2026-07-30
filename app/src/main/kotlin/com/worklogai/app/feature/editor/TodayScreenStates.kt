package com.worklogai.app.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.worklogai.app.R
import com.worklogai.app.core.designsystem.component.WorkLogErrorState
import com.worklogai.app.core.designsystem.component.WorkLogLoadingState
import com.worklogai.app.core.designsystem.component.WorkLogPageHeader
import com.worklogai.app.core.designsystem.component.WorkLogStatusChip
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import com.worklogai.app.feature.editor.component.SaveStatusIndicator
import java.time.LocalDate

@Composable
internal fun TodayHeader(
    presentation: TodayHeaderPresentation,
    saveState: SaveState,
    onRetrySave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.small)) {
        WorkLogPageHeader(
            eyebrow = if (presentation.title == null) "今天" else "固定日期",
            title = presentation.title ?: "${presentation.date.dayOfMonth}日",
            subtitle =
                "${presentation.dateText} · 待办 ${presentation.doneCount}/${presentation.todoCount}" +
                    " · 工作记录 ${presentation.blockCount} 条",
            metrics = {
                if (presentation.inProgressCount > 0) {
                    WorkLogStatusChip(
                        label = "进行中 ${presentation.inProgressCount}",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            },
        )
        SaveStatusIndicator(saveState = saveState, onRetry = onRetrySave)
    }
}

internal data class TodayHeaderPresentation(
    val date: LocalDate,
    val title: String?,
    val dateText: String,
    val todoCount: Int,
    val doneCount: Int,
    val inProgressCount: Int,
    val blockCount: Int,
)

@Composable
internal fun LoadingContent(modifier: Modifier = Modifier) {
    WorkLogLoadingState(
        label = stringResource(R.string.today_loading),
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
internal fun LoadErrorContent(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkLogErrorState(
        title = state.errorMessage ?: "工作记录加载失败",
        body = stringResource(R.string.today_empty_body),
        onRetry = { onAction(TodayAction.RetryLoad) },
        modifier = modifier.fillMaxSize(),
    )
}
