package com.worklogai.app.feature.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyShowsSearchModesAndOpensSummaryCard() {
        val actions = mutableListOf<HistoryAction>()

        composeRule.setContent {
            WorkLogTheme {
                HistoryScreenContent(
                    state = state(items = listOf(item())),
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("搜索工作记录").assertIsDisplayed()
        composeRule.onNodeWithText("最近").assertIsDisplayed()
        composeRule.onNodeWithText("按日").assertIsDisplayed()
        composeRule.onNodeWithText("按周").assertIsDisplayed()
        composeRule.onNodeWithText("按月").assertIsDisplayed()
        composeRule.onNodeWithText("完成历史页面").performClick()
        composeRule.runOnIdle { assertEquals(HistoryAction.OpenEntry(LocalDate.of(2026, 7, 12)), actions.last()) }
    }

    @Test
    fun dayEmptyStateStartsFixedDateEditorAndperiodControlsAreAccessible() {
        val actions = mutableListOf<HistoryAction>()
        val state = state(mode = HistoryMode.DAY)

        composeRule.setContent {
            WorkLogTheme {
                HistoryScreenContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("这一天还没有工作记录").assertIsDisplayed()
        composeRule.onNodeWithText("开始记录").performClick()
        composeRule.onNodeWithContentDescription("上一个日期范围").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下一个日期范围").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(HistoryAction.OpenEntry(LocalDate.of(2026, 7, 12)), actions.first()) }
    }

    @Test
    fun loadMoreAndRetryActionsAreExposed() {
        val actions = mutableListOf<HistoryAction>()
        val state = state(items = listOf(item()), canLoadMore = true, errorMessage = "更多记录加载失败")

        composeRule.setContent {
            WorkLogTheme {
                HistoryScreenContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("加载更多").performClick()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertEquals(listOf(HistoryAction.LoadMore, HistoryAction.Retry), actions) }
    }

    private fun state(
        mode: HistoryMode = HistoryMode.RECENT,
        items: List<HistoryItemUiModel> = emptyList(),
        canLoadMore: Boolean = false,
        errorMessage: String? = null,
    ) = HistoryUiState(
        mode = mode,
        selectedDate = LocalDate.of(2026, 7, 12),
        selectedWeekStart = LocalDate.of(2026, 7, 6),
        selectedMonth = YearMonth.of(2026, 7),
        items = items,
        isLoading = false,
        canLoadMore = canLoadMore,
        errorMessage = errorMessage,
    )

    private fun item() =
        HistoryItemUiModel(
            entryId = "entry",
            date = LocalDate.of(2026, 7, 12),
            dateLabel = "7月12日",
            weekdayLabel = "周日",
            previewText = "完成历史页面",
            contentSummary = "1 条文字",
        )
}
