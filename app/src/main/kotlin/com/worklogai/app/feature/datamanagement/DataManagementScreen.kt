package com.worklogai.app.feature.datamanagement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("数据管理", style = MaterialTheme.typography.headlineSmall)
            Text(
                "导出和备份只会在您主动选择文件位置后执行。完整备份默认不加密，且不包含 API Key。",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.exportDate,
                onValueChange = { onAction(DataManagementAction.ExportDateChanged(it)) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "导出日期" },
                label = { Text("导出日期") },
                supportingText = { Text("格式：yyyy-MM-dd") },
                singleLine = true,
            )
            MarkdownExportSection(state, onAction)
            BackupSection(state, onAction)
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.warningMessage?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            if (state.requiresRecovery) {
                Text("恢复未能完整回滚，请重新启动后检查数据。", color = MaterialTheme.colorScheme.error)
            }
            if (state.wasCancelled) Text("操作已取消")
            state.operation?.let { operation ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(operation.label())
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("导出 Markdown", style = MaterialTheme.typography.titleLarge)
        Text("导出的文件用于阅读和分享，不包含内部 ID、图片路径或 API 配置。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAction(DataManagementAction.ExportEntryMarkdown) },
                enabled = !state.isBusy,
                modifier = Modifier.semantics { contentDescription = "导出工作日志 Markdown" },
            ) { Text("导出日志") }
            Button(
                onClick = { onAction(DataManagementAction.ExportSummaryMarkdown(SummaryType.WEEKLY)) },
                enabled = !state.isBusy,
                modifier = Modifier.semantics { contentDescription = "导出周报 Markdown" },
            ) { Text("导出周报") }
            Button(
                onClick = { onAction(DataManagementAction.ExportSummaryMarkdown(SummaryType.MONTHLY)) },
                enabled = !state.isBusy,
                modifier = Modifier.semantics { contentDescription = "导出月报 Markdown" },
            ) { Text("导出月报") }
        }
    }
}

@Composable
private fun BackupSection(
    state: DataManagementUiState,
    onAction: (DataManagementAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("完整备份与恢复", style = MaterialTheme.typography.titleLarge)
        Text("完整备份包含工作日志、总结、图片和非敏感设置；不会包含 API Key 或 Keystore 数据。")
        Button(
            onClick = { onAction(DataManagementAction.CreateCompleteBackup) },
            enabled = !state.isBusy,
            modifier = Modifier.semantics { contentDescription = "创建完整备份" },
        ) { Text("创建完整备份") }
        TextButton(
            onClick = { onAction(DataManagementAction.ChooseBackupToRestore) },
            enabled = !state.isBusy,
            modifier = Modifier.semantics { contentDescription = "从备份恢复" },
        ) { Text("从备份恢复") }
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
                Text("恢复将完整替换当前工作日志、总结、图片和非敏感设置。当前数据会在恢复成功前保持不变。")
                Text(
                    text =
                        "${preview.entryCount} 条日志 · ${preview.blockCount} 个内容块 · " +
                            "${preview.attachmentCount} 个附件 · ${preview.summaryCount} 份总结",
                )
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
