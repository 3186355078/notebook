package com.worklogai.app.feature.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TodayScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun imageBlockShowsMissingFileCaptionAndDeleteControls() {
        val actions = mutableListOf<TodayAction>()

        composeRule.setContent {
            WorkLogTheme {
                TodayScreenContent(
                    state = stateWith(imageBlock()),
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("图片文件已不存在").assertIsDisplayed()
        composeRule.onNodeWithText("图片说明（可选）").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多图片操作").performClick()
        composeRule.onNodeWithText("删除记录").performClick()
        composeRule.runOnIdle { assertEquals(TodayAction.RequestDeleteBlock("image"), actions.last()) }
    }

    @Test
    fun tableBlockExposesEditableFieldsAndStructureControls() {
        val actions = mutableListOf<TodayAction>()

        composeRule.setContent {
            WorkLogTheme {
                TodayScreenContent(
                    state = stateWith(tableBlock()),
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("表格标题（可选）").assertIsDisplayed()
        composeRule.onNodeWithText("列名").assertIsDisplayed()
        composeRule.onNodeWithText("添加一行").performClick()
        composeRule.onNodeWithText("添加一列").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("标题")
        composeRule.runOnIdle {
            assertEquals(TodayAction.AddTableRow("table"), actions[0])
            assertEquals(TodayAction.AddTableColumn("table"), actions[1])
            assertEquals(TodayAction.TableTitleChanged("table", "标题"), actions.last())
        }
    }

    @Test
    fun imageImportingDisablesPickerAndMixedBlocksUseAvailableActions() {
        var pickerCalls = 0
        val actions = mutableListOf<TodayAction>()
        val state =
            stateWith(
                TextBlockUiModel("text", 0, "text", false, false),
                imageBlock(order = 1),
                tableBlock(order = 2),
            ).copy(isImageImporting = true)

        composeRule.setContent {
            WorkLogTheme {
                TodayScreenContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                    onPickImage = { pickerCalls++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("添加图片").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("添加文字").performClick()
        composeRule.onNodeWithContentDescription("添加表格").performClick()
        composeRule.runOnIdle {
            assertEquals(0, pickerCalls)
            assertEquals(listOf(TodayAction.AddTextBlock, TodayAction.AddTableBlock), actions)
        }
    }

    private fun stateWith(vararg blocks: EditorBlockUiModel): TodayUiState =
        TodayUiState(
            date = LocalDate.of(2026, 7, 12),
            isLoading = false,
            entryId = "entry",
            blocks = blocks.toList(),
        )

    private fun imageBlock(order: Int = 0) =
        ImageBlockUiModel(
            id = "image",
            order = order,
            attachmentId = "attachment",
            relativePath = "images/missing.jpg",
            caption = "",
            isSaving = false,
            hasSaveError = false,
        )

    private fun tableBlock(order: Int = 0) =
        TableBlockUiModel(
            id = "table",
            order = order,
            content =
                TableContent(
                    columns = listOf(TableColumn("column", "列1")),
                    rows = listOf(TableRow("row", mapOf("column" to ""))),
                ),
            isSaving = false,
            hasSaveError = false,
        )
}
