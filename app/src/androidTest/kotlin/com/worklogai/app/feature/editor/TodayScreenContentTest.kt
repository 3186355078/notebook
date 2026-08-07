package com.worklogai.app.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        composeRule.onNodeWithText("图片文件已不存在").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("图片说明（可选）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多图片操作").performScrollTo().performClick()
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
        composeRule.onNodeWithTag("table_add_row_table").performScrollTo().performClick()
        composeRule.onNodeWithTag("table_add_column_table").performScrollTo().performClick()
        composeRule.onNodeWithTag("table_title_table").performTextInput("标题")
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
            assertEquals(
                listOf(TodayAction.AddTextBlock, TodayAction.AddTableBlock),
                actions.filter {
                    it == TodayAction.AddTextBlock || it == TodayAction.AddTableBlock
                },
            )
        }
    }

    @Test
    fun completeFiftyByEightTableRendersAllCellsAndDisablesGrowthAtTheLimits() {
        val actions = mutableListOf<TodayAction>()
        val columns = (1..8).map { index -> TableColumn("column-$index", "Column $index") }
        val rows =
            (1..50).map { rowIndex ->
                TableRow(
                    id = "row-$rowIndex",
                    cells = columns.associate { column -> column.id to "R$rowIndex-${column.id}" },
                )
            }
        val startedAt = android.os.SystemClock.elapsedRealtime()

        composeRule.setContent {
            WorkLogTheme {
                TodayScreenContent(
                    state =
                        stateWith(
                            TableBlockUiModel(
                                id = "large-table",
                                order = 0,
                                content = TableContent(title = "Stage 9 scale table", columns = columns, rows = rows),
                                isSaving = false,
                                hasSaveError = false,
                            ),
                        ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                )
            }
        }
        composeRule.waitForIdle()
        android.util.Log.i(
            "Stage9Metrics",
            "table_50x8_render_ms=${android.os.SystemClock.elapsedRealtime() - startedAt}",
        )

        composeRule.onNodeWithTag("table_add_row_large-table").assertIsNotEnabled()
        composeRule.onNodeWithTag("table_add_column_large-table").assertIsNotEnabled()
        val fields = composeRule.onAllNodes(hasSetTextAction())
        val fieldCount = fields.fetchSemanticsNodes().size
        assertTrue(fieldCount >= EXPECTED_TABLE_FIELDS)
        composeRule.onNodeWithTag("table_cell_row-50_column-8").performScrollTo()
        composeRule.onNodeWithTag("table_cell_row-50_column-8").performTextInput("末格验证")
        composeRule.runOnIdle {
            assertTrue(actions.last() is TodayAction.TableCellChanged)
        }
    }

    @Test
    fun wideLayoutConstrainsTodayToAReadableContentWidth() {
        composeRule.setContent {
            WorkLogTheme {
                Box(modifier = Modifier.requiredSize(width = 900.dp, height = 700.dp)) {
                    TodayScreenContent(
                        state = stateWith(),
                        snackbarHostState = remember { SnackbarHostState() },
                        onAction = {},
                    )
                }
            }
        }

        val contentBounds =
            composeRule
                .onNodeWithTag("today_content_container")
                .getUnclippedBoundsInRoot()
        assertTrue(contentBounds.right - contentBounds.left <= 640.dp)
    }

    @Test
    fun missingHistoricalDateShowsLegalEmptyEditorAndAllContentActions() {
        val actions = mutableListOf<TodayAction>()
        var imagePickerCalls = 0

        composeRule.setContent {
            WorkLogTheme {
                TodayScreenContent(
                    state =
                        TodayUiState(
                            date = LocalDate.of(2026, 7, 8),
                            followsCurrentDate = false,
                            isLoading = false,
                            entryId = null,
                        ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                    onPickImage = { imagePickerCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("历史记录").assertIsDisplayed()
        composeRule.onNodeWithText("这一天还没有工作记录").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("补充当天做过的事情吧").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加文字").performClick()
        composeRule.onNodeWithContentDescription("添加图片").performClick()
        composeRule.onNodeWithContentDescription("添加表格").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(TodayAction.AddTextBlock, TodayAction.AddTableBlock), actions)
            assertEquals(1, imagePickerCalls)
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

    private companion object {
        const val EXPECTED_TABLE_FIELDS = 409
    }
}
