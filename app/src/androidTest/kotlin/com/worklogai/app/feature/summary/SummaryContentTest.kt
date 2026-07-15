package com.worklogai.app.feature.summary

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import org.junit.Rule
import org.junit.Test
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
        composeRule.onNodeWithText("生成总结").assertIsDisplayed()
        composeRule.onNodeWithText("将发送该时间范围内允许用于 AI 总结的文字、图片说明和表格内容。").assertIsDisplayed()
    }
}
