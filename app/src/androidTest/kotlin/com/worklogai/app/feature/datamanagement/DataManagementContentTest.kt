package com.worklogai.app.feature.datamanagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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

        composeRule.onNodeWithText("创建完整备份").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("从备份恢复").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("数据管理").assertCountEquals(0)
        composeRule
            .onNodeWithText(
                "只有主动选择文件位置后才会执行；API Key 不包含在备份中。",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun rendersRestorePreviewCountsWarningsAndConfirmation() {
        render(
            DataManagementUiState(
                exportDate = "2026-07-14",
                restorePreview =
                    BackupPreview(
                        createdAt = Instant.parse("2026-07-14T08:00:00Z"),
                        entryCount = 2,
                        blockCount = 3,
                        attachmentCount = 1,
                        summaryCount = 1,
                        warningCount = 1,
                        todoCount = 4,
                    ),
            ),
        )

        composeRule.onNodeWithText("确认恢复备份？").assertIsDisplayed()
        composeRule.onNodeWithText("2 条日志", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("4 条待办").assertIsDisplayed()
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

        composeRule.onNodeWithText("正在恢复备份…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("创建完整备份").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("从备份恢复").performScrollTo().assertIsNotEnabled()
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

        composeRule.onNodeWithText("自动任务将在下次启动协调").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("恢复未能完整回滚", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rendersCancelledStateWithoutAFalseSuccessMessage() {
        render(DataManagementUiState(exportDate = "2026-07-14", wasCancelled = true))

        composeRule.onNodeWithText("操作已取消").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun exposesAccessibleDescriptionsForAllPrimaryFileActions() {
        render(DataManagementUiState(exportDate = "2026-07-14"))

        composeRule.onNodeWithContentDescription("导出工作日志 Markdown").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导出周报 Markdown").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导出月报 Markdown").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("创建完整备份").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("从备份恢复").performScrollTo().assertIsDisplayed()
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
