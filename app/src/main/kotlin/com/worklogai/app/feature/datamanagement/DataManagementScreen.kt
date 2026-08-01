package com.worklogai.app.feature.datamanagement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worklogai.app.core.designsystem.component.WorkLogActionRow
import com.worklogai.app.core.designsystem.component.WorkLogActionRowContent
import com.worklogai.app.core.designsystem.component.WorkLogContentSurface
import com.worklogai.app.core.designsystem.component.WorkLogSection
import com.worklogai.app.core.designsystem.component.WorkLogStatusChip
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import com.worklogai.app.core.model.SummaryType

@Composable
fun DataManagementScreen(
    onRestoreCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val markdownLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
            viewModel.onMarkdownDocumentSelected(it)
        }
    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
            viewModel.onBackupDocumentSelected(it)
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            viewModel.onBackupRestoreDocumentSelected(it)
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DataManagementUiEvent.CreateMarkdownDocument -> markdownLauncher.launch(event.suggestedFileName)
                is DataManagementUiEvent.CreateBackupDocument -> backupLauncher.launch(event.suggestedFileName)
                DataManagementUiEvent.OpenBackupDocument ->
                    restoreLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream"),
                    )
                is DataManagementUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                DataManagementUiEvent.RestoreCompleted -> onRestoreCompleted()
            }
        }
    }
    DataManagementContent(
        state = state,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
internal fun DataManagementContent(
    state: DataManagementUiState,
    snackbarHost: @Composable () -> Unit,
    onAction: (DataManagementAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = snackbarHost,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(WorkLogSpacing.largePlus),
            verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.extraLarge),
        ) {
            WorkLogContentSurface(emphasized = true) {
                Column(
                    modifier = Modifier.padding(WorkLogSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.extraSmall),
                ) {
                    Text("完整备份默认未加密", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "只有主动选择文件位置后才会执行；API Key 不包含在备份中。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedTextField(
                value = state.exportDate,
                onValueChange = { onAction(DataManagementAction.ExportDateChanged(it)) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "导出日期" },
                label = { Text("导出日期") },
                supportingText = { Text("格式：yyyy-MM-dd") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            MarkdownExportSection(state, onAction)
            BackupSection(state, onAction)
            state.errorMessage?.let {
                WorkLogStatusChip(
                    label = it,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            state.warningMessage?.let {
                WorkLogStatusChip(
                    label = it,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (state.requiresRecovery) {
                Text("恢复未能完整回滚，请重新启动后检查数据。", color = MaterialTheme.colorScheme.error)
            }
            if (state.wasCancelled) Text("操作已取消")
            state.operation?.let { operation ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(operation.label(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    state.restorePreview?.let { preview ->
        RestoreConfirmationDialog(
            preview = preview,
            onConfirm = { onAction(DataManagementAction.ConfirmRestore) },
            onDismiss = { onAction(DataManagementAction.DismissRestorePreview) },
        )
    }
}

@Composable
private fun MarkdownExportSection(
    state: DataManagementUiState,
    onAction: (DataManagementAction) -> Unit,
) {
    WorkLogSection(
        title = "Markdown 导出",
        description = "用于阅读和分享，不包含内部 ID、图片路径或 API 配置。",
    ) {
        WorkLogActionRow(
            content =
                WorkLogActionRowContent(
                    icon = Icons.Outlined.Description,
                    title = "导出当日日志",
                    summary = "生成包含工作记录和可选待办的 Markdown",
                ),
            onClick =
                if (state.isBusy) {
                    null
                } else {
                    { onAction(DataManagementAction.ExportEntryMarkdown) }
                },
            modifier = Modifier.semantics { contentDescription = "导出工作日志 Markdown" },
        )
        WorkLogActionRow(
            content =
                WorkLogActionRowContent(
                    icon = Icons.Outlined.Description,
                    title = "导出周报",
                    summary = "将当前周报保存为便于阅读和分享的 Markdown",
                ),
            onClick =
                if (state.isBusy) {
                    null
                } else {
                    { onAction(DataManagementAction.ExportSummaryMarkdown(SummaryType.WEEKLY)) }
                },
            modifier = Modifier.semantics { contentDescription = "导出周报 Markdown" },
        )
        WorkLogActionRow(
            content =
                WorkLogActionRowContent(
                    icon = Icons.Outlined.Description,
                    title = "导出月报",
                    summary = "将当前月报保存为便于阅读和分享的 Markdown",
                ),
            onClick =
                if (state.isBusy) {
                    null
                } else {
                    { onAction(DataManagementAction.ExportSummaryMarkdown(SummaryType.MONTHLY)) }
                },
            modifier = Modifier.semantics { contentDescription = "导出月报 Markdown" },
        )
    }
}

@Composable
private fun BackupSection(
    state: DataManagementUiState,
    onAction: (DataManagementAction) -> Unit,
) {
    WorkLogSection(
        title = "完整备份与恢复",
        description = "完整备份默认未加密；包含工作日志、待办、总结、图片和非敏感设置。",
    ) {
        WorkLogActionRow(
            content =
                WorkLogActionRowContent(
                    icon = Icons.Outlined.Archive,
                    title = "创建完整备份",
                    summary = "包含工作日志、待办、总结、图片和非敏感设置",
                ),
            onClick =
                if (state.isBusy) {
                    null
                } else {
                    { onAction(DataManagementAction.CreateCompleteBackup) }
                },
            modifier = Modifier.semantics { contentDescription = "创建完整备份" },
        )
        WorkLogActionRow(
            content =
                WorkLogActionRowContent(
                    icon = Icons.Outlined.Restore,
                    title = "从备份恢复",
                    summary = "预检通过并确认后，完整替换当前本地数据",
                ),
            onClick =
                if (state.isBusy) {
                    null
                } else {
                    { onAction(DataManagementAction.ChooseBackupToRestore) }
                },
            modifier = Modifier.semantics { contentDescription = "从备份恢复" },
        )
        Text(
            "API Key 和 Keystore 数据不会进入备份。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RestoreConfirmationDialog(
    preview: com.worklogai.app.core.backup.BackupPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认恢复备份？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("恢复将完整替换当前工作日志、待办、总结、图片和非敏感设置。当前数据会在恢复成功前保持不变。")
                Text(
                    text =
                        "${preview.entryCount} 条日志 · ${preview.blockCount} 个内容块 · " +
                            "${preview.todoCount} 条待办 · ${preview.attachmentCount} 个附件 · " +
                            "${preview.summaryCount} 份总结",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small)) {
                    WorkLogStatusChip(label = "${preview.todoCount} 条待办")
                    WorkLogStatusChip(label = "${preview.attachmentCount} 个附件")
                    WorkLogStatusChip(label = "${preview.summaryCount} 份总结")
                }
                if (preview.warningCount > 0) Text("备份中有 ${preview.warningCount} 个缺失图片文件。")
                Text("API Key 不会从备份恢复，也不会被删除。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.semantics { contentDescription = "确认替换当前数据" }) {
                Text("确认恢复")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun DataManagementOperation.label(): String =
    when (this) {
        DataManagementOperation.SELECTING_MARKDOWN_DESTINATION -> "等待选择导出位置…"
        DataManagementOperation.SELECTING_BACKUP_DESTINATION -> "等待选择备份位置…"
        DataManagementOperation.SELECTING_RESTORE_SOURCE -> "等待选择备份文件…"
        DataManagementOperation.EXPORTING -> "正在导出…"
        DataManagementOperation.CREATING_BACKUP -> "正在创建备份…"
        DataManagementOperation.INSPECTING_BACKUP -> "正在检查备份…"
        DataManagementOperation.RESTORING_BACKUP -> "正在恢复备份…"
    }
