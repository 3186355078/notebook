package com.worklogai.app.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkLogAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsOnTodayAndCanOpenHistory() {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText("今天")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("历史").performClick()

        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(hasContentDescription("搜索历史记录"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodes(hasContentDescription("搜索历史记录"))[0].assertIsDisplayed()
    }

    @Test
    fun todayCanAddAndEditATextBlock() {
        val addText = hasText("添加文字") or hasContentDescription("添加文字")
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(addText).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(addText)[0].performClick()

        val textFields = composeRule.onAllNodes(hasSetTextAction())
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            textFields.fetchSemanticsNodes().isNotEmpty()
        }
        val newestFieldIndex = textFields.fetchSemanticsNodes().lastIndex
        textFields[newestFieldIndex].performTextInput("完成阶段三验证")
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 10_000L
    }
}
