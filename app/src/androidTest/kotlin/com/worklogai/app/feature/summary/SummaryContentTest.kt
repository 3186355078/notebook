package com.worklogai.app.feature.summary

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class SummaryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersManualWeeklySummaryControls() {
        composeRule.setContent {
            MaterialTheme {
                SummaryContent(
                    state =
                        SummaryUiState(
                            summaryType = SummaryType.WEEKLY,
                            period = DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)),
                            selectedMonth = YearMonth.of(2026, 7),
                            isLoading = false,
                        ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("工作总结").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上一周").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下一周").assertIsDisplayed()
        composeRule.onNodeWithText("回到本周").assertIsDisplayed()
        composeRule.onNodeWithText("生成总结").assertIsDisplayed()
        composeRule.onNodeWithText("将发送该时间范围内允许用于 AI 总结的文字、图片说明和表格内容。").assertIsDisplayed()
    }

    @Test
    fun successfulSummaryPrioritizesReadingAndKeepsSecondaryActionsInOverflow() {
        val actions = mutableListOf<SummaryAction>()
        val period = DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12))
        composeRule.setContent {
            MaterialTheme {
                SummaryContent(
                    state =
                        SummaryUiState(
                            summaryType = SummaryType.WEEKLY,
                            period = period,
                            selectedMonth = YearMonth.of(2026, 7),
                            isLoading = false,
                            summary =
                                WorkSummary(
                                    id = "summary",
                                    summaryType = SummaryType.WEEKLY,
                                    periodStart = period.start,
                                    periodEnd = period.end,
                                    status = SummaryStatus.SUCCESS,
                                    sourceHash = "hash",
                                    aiProvider = "mock",
                                    modelName = "mock",
                                    originalContent = "本期总结内容",
                                    editedContent = null,
                                    errorMessage = null,
                                    createdAt = Instant.EPOCH,
                                    updatedAt = Instant.EPOCH,
                                    generatedAt = Instant.EPOCH,
                                ),
                        ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("本期总结内容").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多总结操作").performClick()
        composeRule.onNodeWithText("复制").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(SummaryAction.CopySummary, actions.last()) }
    }
}
