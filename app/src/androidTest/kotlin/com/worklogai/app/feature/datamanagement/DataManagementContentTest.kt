package com.worklogai.app.feature.datamanagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.worklogai.app.core.backup.BackupPreview
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class DataManagementContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersExportAndBackupActionsWithSecurityNotice() {
        composeRule.setContent {
            MaterialTheme {
                DataManagementContent(
                    state = DataManagementUiState(exportDate = "2026-07-14"),
                    snackbarHost = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("数据管理").assertIsDisplayed()
        composeRule.onNodeWithText("创建完整备份").assertIsDisplayed()
        composeRule.onNodeWithText("从备份恢复").assertIsDisplayed()
        composeRule.onNodeWithText("API Key", substring = true).assertIsDisplayed()
    }

    @Test
    fun rendersRestorePreviewCountsWarningsAndConfirmation() {
        render(
            DataManagementUiState(
                exportDate = "2026-07-14",
                restorePreview = BackupPreview(Instant.parse("2026-07-14T08:00:00Z"), 2, 3, 1, 1, 1),
            ),
        )

        composeRule.onNodeWithText("确认恢复备份？").assertIsDisplayed()
        composeRule.onNodeWithText("2 条日志", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("1 个缺失图片", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("确认替换当前数据").assertIsDisplayed()
    }

    @Test
    fun busyRestoreStateDisablesReentrantActions() {
        render(
            DataManagementUiState(
                exportDate = "2026-07-14",
                operation = DataManagementOperation.RESTORING_BACKUP,
            ),
        )

        composeRule.onNodeWithText("正在恢复备份…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("创建完整备份").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("从备份恢复").assertIsNotEnabled()
    }

    @Test
    fun rendersSchedulerWarningAndSeriousRecoveryState() {
        render(
            DataManagementUiState(
                exportDate = "2026-07-14",
                warningMessage = "自动任务将在下次启动协调",
                requiresRecovery = true,
            ),
        )

        composeRule.onNodeWithText("自动任务将在下次启动协调").assertIsDisplayed()
        composeRule.onNodeWithText("恢复未能完整回滚", substring = true).assertIsDisplayed()
    }

    @Test
    fun rendersCancelledStateWithoutAFalseSuccessMessage() {
        render(DataManagementUiState(exportDate = "2026-07-14", wasCancelled = true))

        composeRule.onNodeWithText("操作已取消").assertIsDisplayed()
    }

    @Test
    fun exposesAccessibleDescriptionsForAllPrimaryFileActions() {
        render(DataManagementUiState(exportDate = "2026-07-14"))

        composeRule.onNodeWithContentDescription("导出工作日志 Markdown").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导出周报 Markdown").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导出月报 Markdown").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("创建完整备份").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("从备份恢复").assertIsDisplayed()
    }

    private fun render(state: DataManagementUiState) {
        composeRule.setContent {
            MaterialTheme {
                DataManagementContent(
                    state = state,
                    snackbarHost = {},
                    onAction = {},
                )
            }
        }
    }
}
