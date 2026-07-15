package com.worklogai.app.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
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
        composeRule.onNodeWithText("记录今天的工作").assertIsDisplayed()

        composeRule.onNodeWithText("历史").performClick()

        composeRule.onNodeWithText("还没有历史记录").assertIsDisplayed()
    }

    @Test
    fun todayCanAddAndEditATextBlock() {
        composeRule.onNodeWithText("添加文字").performClick()

        composeRule.onNode(hasSetTextAction()).performTextInput("完成阶段三验证")
    }
}
