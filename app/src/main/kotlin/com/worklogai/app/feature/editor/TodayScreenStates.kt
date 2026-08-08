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
            eyebrow =
                if (presentation.title == null) {
                    val remaining = (presentation.todoCount - presentation.doneCount).coerceAtLeast(0)
                    if (remaining == 0) "今天的待办已完成" else "今天还有 $remaining 项待办"
                } else {
                    "历史记录"
                },
            title = presentation.title ?: presentation.dateText,
            subtitle =
                "完成 ${presentation.doneCount}/${presentation.todoCount} · " +
                    "进行中 ${presentation.inProgressCount} · 工作记录 ${presentation.blockCount} 条",
            metrics = {
                WorkLogStatusChip(
                    label = if (presentation.todoCount == 0) "尚未安排" else "今日概览",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
