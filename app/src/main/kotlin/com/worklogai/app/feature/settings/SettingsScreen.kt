package com.worklogai.app.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worklogai.app.BuildConfig
import com.worklogai.app.core.designsystem.component.WorkLogActionRow
import com.worklogai.app.core.designsystem.component.WorkLogActionRowContent
import com.worklogai.app.core.designsystem.component.WorkLogPageHeader
import com.worklogai.app.core.designsystem.component.WorkLogSection
import com.worklogai.app.core.designsystem.theme.WorkLogIndicatorSize
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenDataManagement: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onAction(AiSettingsAction.NotificationPermissionStateChanged(granted))
        }
    LaunchedEffect(Unit) {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        viewModel.onAction(AiSettingsAction.NotificationPermissionStateChanged(granted))
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AiSettingsUiEvent.RequestNotificationPermission -> {
                    val notificationPermissionMissing =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    if (notificationPermissionMissing) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                is AiSettingsUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        SettingsContent(
            state = state,
            onAction = viewModel::onAction,
            onOpenDataManagement = onOpenDataManagement,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun SettingsContent(
    state: AiSettingsUiState,
    onAction: (AiSettingsAction) -> Unit,
    onOpenDataManagement: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showApiKey by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WorkLogSpacing.largePlus),
        verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.large),
    ) {
        WorkLogPageHeader(
            eyebrow = "偏好与安全",
            title = "设置",
            subtitle = "集中管理外观、AI 服务、自动总结与本地数据",
        )
        WorkLogSection(
            title = "外观",
            description = "跟随系统浅色/深色模式；Android 12 及以上默认使用系统动态配色。",
        ) {
            AppearanceInfoRow()
        }
        WorkLogSection(title = "AI 服务") {
            ProviderModeControl(state, onAction)
            ProviderConfiguration(state, showApiKey, onAction, onShowApiKeyChanged = { showApiKey = it })
            SettingsActions(state, onAction)
        }
        WorkLogSection(
            title = "自动总结",
            description = "默认关闭，仅处理已经结束且允许用于 AI 的工作记录周期；下次检查由系统调度，时间不保证精确。",
        ) {
            AutoSummarySection(state, onAction)
        }
        WorkLogSection(title = "数据管理") {
            WorkLogActionRow(
                content =
                    WorkLogActionRowContent(
                        icon = Icons.Outlined.DataObject,
                        title = "导出、备份与恢复",
                        summary = "导出 Markdown，或管理包含待办的完整本地备份",
                    ),
                onClick = onOpenDataManagement,
                modifier = Modifier.semantics { contentDescription = "数据管理" },
            )
        }
        WorkLogSection(title = "隐私与安全") {
            Text(
                "API Key 仅以 Android Keystore 保护的密文保存在本机，不会写入工作日志、待办或备份。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "待办不会自动发送给 AI；只有主动同步为允许 AI 处理的工作记录后才可能参与总结。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        WorkLogSection(title = "关于") {
            Text("WorkLog AI ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (state.showAutoSummaryConsent) {
        AutoSummaryConsentDialog(
            onConfirm = { onAction(AiSettingsAction.ConfirmAutoSummaryConsent) },
            onDismiss = { onAction(AiSettingsAction.DismissAutoSummaryConsent) },
        )
    }
}

@Composable
private fun AppearanceInfoRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = WorkLogSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                modifier = Modifier.padding(WorkLogSpacing.small).size(22.dp),
            )
        }
        Column {
            Text("系统主题", style = MaterialTheme.typography.titleMedium)
            Text(
                "无需手动切换，浅色、深色与动态配色均由系统决定",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProviderModeControl(
    state: AiSettingsUiState,
    onAction: (AiSettingsAction) -> Unit,
) {
    SettingSwitchRow(
        title = "使用模拟服务",
        description = "无需 API Key，可完整演示周报和月报生成。",
        checked = state.settings.useMockProvider,
        descriptionForAccessibility = "使用模拟服务",
        onCheckedChange = { value -> onAction(AiSettingsAction.UseMockChanged(value)) },
        showDivider = false,
    )
}

@Composable
private fun ProviderConfiguration(
    state: AiSettingsUiState,
    showApiKey: Boolean,
    onAction: (AiSettingsAction) -> Unit,
    onShowApiKeyChanged: (Boolean) -> Unit,
) {
    SettingsGroupLabel("服务配置")
    OutlinedTextField(
        value = state.settings.baseUrl,
        onValueChange = { value -> onAction(AiSettingsAction.BaseUrlChanged(value)) },
        enabled = !state.settings.useMockProvider && !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Base URL") },
        placeholder = { Text("https://example.com/v1") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
    OutlinedTextField(
        value = state.settings.model,
        onValueChange = { value -> onAction(AiSettingsAction.ModelChanged(value)) },
        enabled = !state.settings.useMockProvider && !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("模型名称") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
    OutlinedTextField(
        value =
            state.settings.timeoutSeconds
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty(),
        onValueChange = { value -> onAction(AiSettingsAction.TimeoutChanged(value)) },
        enabled = !state.isLoading,
        modifier = Modifier.widthIn(max = 180.dp),
        label = { Text("超时时间（秒）") },
        supportingText = { Text("10—120 秒") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
    SettingsGroupLabel("安全")
    OutlinedTextField(
        value = state.apiKeyInput,
        onValueChange = { value -> onAction(AiSettingsAction.ApiKeyChanged(value)) },
        enabled = !state.settings.useMockProvider && !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(if (state.hasApiKey) "API Key（已配置）" else "API Key") },
        placeholder = { Text(if (state.hasApiKey) "留空则保留已保存的密钥" else "输入后安全保存") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { onShowApiKeyChanged(!showApiKey) }) {
                Icon(
                    imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showApiKey) "隐藏 API Key" else "显示 API Key",
                )
            }
        },
    )
}

@Composable
private fun SettingsGroupLabel(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = WorkLogSpacing.small),
    )
}

@Composable
private fun SettingsActions(
    state: AiSettingsUiState,
    onAction: (AiSettingsAction) -> Unit,
) {
    state.errorMessage?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small),
    ) {
        Button(onClick = { onAction(AiSettingsAction.Save) }, enabled = !state.isSaving && !state.isLoading) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = WorkLogSpacing.small).size(WorkLogIndicatorSize.inline),
                    strokeWidth = 2.dp,
                )
            }
            Text("保存设置")
        }
        OutlinedButton(
            onClick = { onAction(AiSettingsAction.TestConnection) },
            enabled = !state.isTestingConnection && !state.isSaving && !state.isLoading,
        ) { Text(if (state.isTestingConnection) "正在测试…" else "测试连接") }
    }
    if (state.hasApiKey) {
        TextButton(onClick = { onAction(AiSettingsAction.DeleteApiKey) }, enabled = !state.isLoading) {
            Text("删除 API Key")
        }
    }
}

@Composable
private fun AutoSummarySection(
    state: AiSettingsUiState,
    onAction: (AiSettingsAction) -> Unit,
) {
    SettingSwitchRow(
        title = "自动生成周报",
        description = "仅在已结束自然周、设备条件允许时检查。",
        checked = state.settings.autoWeeklySummaryEnabled,
        descriptionForAccessibility = "自动生成周报",
        onCheckedChange = { onAction(AiSettingsAction.AutoWeeklySummaryChanged(it)) },
    )
    SettingSwitchRow(
        title = "自动生成月报",
        description = "仅在已结束自然月、设备条件允许时检查。",
        checked = state.settings.autoMonthlySummaryEnabled,
        descriptionForAccessibility = "自动生成月报",
        onCheckedChange = { onAction(AiSettingsAction.AutoMonthlySummaryChanged(it)) },
    )
    SettingSwitchRow(
        title = "允许使用移动网络",
        description = "关闭后，真实大模型服务仅在非计费网络下运行。",
        checked = state.settings.allowMobileNetwork,
        descriptionForAccessibility = "允许使用移动网络",
        onCheckedChange = { onAction(AiSettingsAction.AllowMobileNetworkChanged(it)) },
    )
    SettingSwitchRow(
        title = "生成完成后通知",
        description = "通知仅提示生成结果，不显示工作内容。",
        checked = state.settings.notifyOnAutoSummaryCompletion,
        descriptionForAccessibility = "生成完成后通知",
        onCheckedChange = { onAction(AiSettingsAction.NotifyOnAutoSummaryCompletionChanged(it)) },
        showDivider = false,
    )
    if (!state.notificationPermissionGranted && state.settings.notifyOnAutoSummaryCompletion) {
        Text("系统通知权限未开启，自动生成仍会正常执行。", style = MaterialTheme.typography.bodySmall)
    }
    state.scheduleState.lastCheckAt?.let { lastCheckAt ->
        Text(
            "最近自动检查：${lastCheckAt.atZone(ZoneId.systemDefault()).format(LAST_CHECK_FORMATTER)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    state.lastAutoFailureMessage?.let { message ->
        Text("最近自动生成失败：$message", color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(
        onClick = { onAction(AiSettingsAction.RunAutoSummaryCheck) },
        enabled = !state.isScheduling && !state.isLoading,
        modifier = Modifier.semantics { contentDescription = "立即检查自动总结" },
    ) {
        if (state.isScheduling) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = WorkLogSpacing.small).size(WorkLogIndicatorSize.inline),
                strokeWidth = 2.dp,
            )
        }
        Text("立即检查一次")
    }
}

@Composable
@Suppress("LongParameterList")
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    descriptionForAccessibility: String,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = WorkLogSpacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = descriptionForAccessibility },
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun AutoSummaryConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启自动总结？") },
        text = {
            Text(
                "自动总结会在已结束的周期内，将允许用于 AI 总结的文字、图片说明和表格内容发送给当前配置的大模型服务。使用真实服务可能产生 API 费用。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认开启") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private val LAST_CHECK_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
