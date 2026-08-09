package com.worklogai.app.feature.summary

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun readingModeRendersMarkdownInsteadOfSourceMarkers() {
        setSummaryContent(markdown = "# 本周总结\n\n## 完成事项\n\n- **完成接口**")

        composeRule.onNodeWithText("本周总结").assertIsDisplayed()
        composeRule.onNodeWithText("完成事项").assertIsDisplayed()
        composeRule.onNodeWithText("完成接口").assertIsDisplayed()
        composeRule.onAllNodesWithText("#", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("**", substring = true).assertCountEquals(0)
    }

    @Test
    fun staleBannerAndRichMarkdownRemainVisibleTogether() {
        setSummaryContent(markdown = "## 旧总结\n\n仍可阅读", isOutdated = true)

        composeRule.onNodeWithText("原记录已更新").assertIsDisplayed()
        composeRule.onNodeWithText("旧总结").assertIsDisplayed()
        composeRule.onNodeWithText("仍可阅读").assertIsDisplayed()
    }

    @Test
    fun failedRegenerationKeepsPreviousMarkdownRendered() {
        setSummaryContent(
            markdown = "## 上次成功内容\n\n- 保留阅读",
            generationState = SummaryGenerationState.Failed("网络超时", hasPreviousContent = true),
        )

        composeRule.onNodeWithText("本次生成未完成").assertIsDisplayed()
        composeRule.onNodeWithText("上次成功内容").assertIsDisplayed()
        composeRule.onAllNodesWithText("##", substring = true).assertCountEquals(0)
    }

    @Test
    fun editingModeKeepsOriginalMarkdownSource() {
        setSummaryContent(markdown = "## 编辑标题\n\n- 原始列表", isEditing = true, preview = false)

        composeRule.onNodeWithText("## 编辑标题\n\n- 原始列表").assertIsDisplayed()
        composeRule.onNodeWithText("编辑 Markdown").assertIsDisplayed()
    }

    @Test
    fun previewModeUsesRichRendererAndCanSwitchBackToEdit() {
        val actions = mutableListOf<SummaryAction>()
        setSummaryContent(
            markdown = "## 预览标题\n\n- **完成测试**",
            isEditing = true,
            preview = true,
            actions = actions,
        )

        composeRule.onNodeWithText("预览标题").assertIsDisplayed()
        composeRule.onNodeWithText("完成测试").assertIsDisplayed()
        composeRule.onAllNodesWithText("##", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.runOnIdle { assertEquals(SummaryAction.ChangeEditingPreview(false), actions.last()) }
    }

    @Test
    fun longMarkdownRemainsVerticallyScrollable() {
        val markdown =
            buildString {
                repeat(120) { append("## 小节 ${it + 1}\n\n- 内容 ${it + 1}\n\n") }
            }
        setSummaryContent(markdown)

        composeRule.onNodeWithText("小节 120").performScrollTo().assertIsDisplayed()
    }

    private fun setSummaryContent(
        markdown: String,
        isOutdated: Boolean = false,
        generationState: SummaryGenerationState = SummaryGenerationState.Success,
        isEditing: Boolean = false,
        preview: Boolean = false,
        actions: MutableList<SummaryAction> = mutableListOf(),
    ) {
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
                            summary = summary(period, markdown),
                            generationState = generationState,
                            isOutdated = isOutdated,
                            isEditing = isEditing,
                            isEditingPreview = preview,
                            editingText = markdown,
                        ),
                    onAction = actions::add,
                )
            }
        }
    }

    private fun summary(
        period: DateRange,
        markdown: String,
    ): WorkSummary =
        WorkSummary(
            id = "markdown-summary",
            summaryType = SummaryType.WEEKLY,
            periodStart = period.start,
            periodEnd = period.end,
            status = SummaryStatus.SUCCESS,
            sourceHash = "hash",
            aiProvider = "mock",
            modelName = "mock",
            originalContent = markdown,
            editedContent = null,
            errorMessage = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            generatedAt = Instant.EPOCH,
        )
}
