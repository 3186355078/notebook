package com.worklogai.app.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worklogai.app.core.designsystem.component.WorkLogContentSurface
import com.worklogai.app.core.designsystem.component.WorkLogPageHeader
import com.worklogai.app.core.designsystem.component.WorkLogStatusChip
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SummaryScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SummaryUiEvent.ConfirmRegenerate -> confirmRegenerate = true
                SummaryUiEvent.ConfirmRestoreOriginal -> confirmRestore = true
                is SummaryUiEvent.CopyText -> clipboard.setText(AnnotatedString(event.value))
                SummaryUiEvent.OpenSettings -> onOpenSettings()
                is SummaryUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        SummaryContent(state, viewModel::onAction)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text("重新生成总结？") },
            text = { Text("重新生成将覆盖当前手动修改的总结。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = false
                    viewModel.onAction(SummaryAction.ConfirmRegenerate)
                }) { Text("重新生成") }
            },
            dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text("取消") } },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复 AI 原始版本？") },
            text = { Text("当前手动修改将被替换。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    viewModel.onAction(SummaryAction.ConfirmRestoreOriginal)
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummaryContent(
    state: SummaryUiState,
    onAction: (SummaryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WorkLogSpacing.largePlus),
        verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.large),
    ) {
        WorkLogPageHeader(
            eyebrow = "AI 工作回顾",
            title = "工作总结",
            subtitle = "聚焦已结束周期，阅读、编辑或重新生成你的周报与月报",
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SummaryType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.summaryType == type,
                    onClick = { onAction(SummaryAction.ChangeType(type)) },
                    shape =
                        androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                            index,
                            SummaryType.entries.size,
                        ),
                    label = { Text(if (type == SummaryType.WEEKLY) "周报" else "月报") },
                )
            }
        }
        WorkLogContentSurface {
            Column(
                modifier = Modifier.padding(WorkLogSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.extraSmall),
            ) {
                PeriodControls(state.summaryType, state.period, onAction)
            }
        }
        Text("将发送该时间范围内允许用于 AI 总结的文字、图片说明和表格内容。", style = MaterialTheme.typography.bodySmall)
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            SummaryLoadedContent(state, onAction)
        }
    }
}

@Composable
private fun SummaryLoadedContent(
    state: SummaryUiState,
    onAction: (SummaryAction) -> Unit,
) {
    Text(
        "共 ${state.entryCount} 条记录，其中 ${state.eligibleEntryCount} 条可用于 AI 总结",
        style = MaterialTheme.typography.bodySmall,
    )
    SummaryFlags(state)
    SummaryGenerationStatus(state.generationState)
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    SummaryEditorOrReader(state, onAction)
    SummaryActions(state, onAction)
}

@Composable
private fun SummaryFlags(state: SummaryUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small)) {
        if (state.isOutdated) {
            WorkLogStatusChip(
                label = "原记录已更新",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (state.wasInputTruncated) {
            WorkLogStatusChip(
                label = "部分内容已截断",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun SummaryGenerationStatus(generation: SummaryGenerationState) {
    when (generation) {
        is SummaryGenerationState.Failed -> {
            Text(generation.message, color = MaterialTheme.colorScheme.error)
            if (generation.hasPreviousContent) {
                Text(
                    "重新生成失败，当前展示上一次结果。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        is SummaryGenerationState.NoEligibleContent ->
            Text(
                if (generation.allEntriesBlocked) "该时间范围内的记录未允许用于 AI 总结" else "该时间范围内没有可用于总结的工作记录",
            )
        SummaryGenerationState.Generating ->
            Row(horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small)) {
                CircularProgressIndicator()
                Text("正在生成总结…")
            }
        else -> Unit
    }
}

@Composable
private fun SummaryEditorOrReader(
    state: SummaryUiState,
    onAction: (SummaryAction) -> Unit,
) {
    if (state.isEditing) {
        OutlinedTextField(
            value = state.editingText,
            onValueChange = { value -> onAction(SummaryAction.EditingTextChanged(value)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("编辑总结") },
            minLines = 12,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.medium)) {
            Button(onClick = { onAction(SummaryAction.SaveEditing) }) { Text("保存") }
            TextButton(onClick = { onAction(SummaryAction.CancelEditing) }) { Text("取消") }
        }
    } else {
        state.displayContent?.let { content ->
            WorkLogContentSurface {
                SelectionContainer {
                    Text(
                        content,
                        modifier = Modifier.padding(WorkLogSpacing.large),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodControls(
    type: SummaryType,
    period: DateRange,
    onAction: (SummaryAction) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onAction(SummaryAction.PreviousPeriod) }, content = {
            Text(
                if (type ==
                    SummaryType.WEEKLY
                ) {
                    "上一周"
                } else {
                    "上一月"
                },
            )
        })
        Column { Text(period.toLabel(), style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = { onAction(SummaryAction.NextPeriod) }, content = {
            Text(
                if (type ==
                    SummaryType.WEEKLY
                ) {
                    "下一周"
                } else {
                    "下一月"
                },
            )
        })
    }
    TextButton(onClick = { onAction(SummaryAction.ReturnToCurrentPeriod) }) {
        Text(
            if (type ==
                SummaryType.WEEKLY
            ) {
                "回到本周"
            } else {
                "回到本月"
            },
        )
    }
}

@Composable
private fun SummaryActions(
    state: SummaryUiState,
    onAction: (SummaryAction) -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.generationState) {
            SummaryGenerationState.Generating ->
                Button(
                    onClick = { onAction(SummaryAction.CancelGeneration) },
                ) { Text("取消生成") }
            is SummaryGenerationState.Failed ->
                Button(
                    onClick = { onAction(SummaryAction.RetryGeneration) },
                ) { Text("重试") }
            else ->
                Button(onClick = { onAction(SummaryAction.Generate) }) {
                    Text(
                        if (state.summary ==
                            null
                        ) {
                            "生成总结"
                        } else {
                            "重新生成"
                        },
                    )
                }
        }
        if (!state.displayContent.isNullOrBlank() && !state.isEditing) {
            TextButton(onClick = { onAction(SummaryAction.StartEditing) }) { Text("编辑") }
            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多总结操作")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            overflowExpanded = false
                            onAction(SummaryAction.CopySummary)
                        },
                    )
                    if (state.summary?.originalContent != null) {
                        DropdownMenuItem(
                            text = { Text("恢复 AI 原始版本") },
                            onClick = {
                                overflowExpanded = false
                                onAction(SummaryAction.RestoreOriginal)
                            },
                        )
                    }
                }
            }
        }
    }
    if (state.generationState is SummaryGenerationState.Failed &&
        state.summary == null
    ) {
        TextButton(onClick = { onAction(SummaryAction.OpenSettings) }) { Text("前往设置") }
    }
}

private fun DateRange.toLabel(): String =
    if (start.year == end.year &&
        start.month == end.month
    ) {
        start.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)) +
            "—" +
            end.format(DateTimeFormatter.ofPattern("d日", Locale.SIMPLIFIED_CHINESE))
    } else {
        start.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)) + "—" +
            end.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE))
    }
