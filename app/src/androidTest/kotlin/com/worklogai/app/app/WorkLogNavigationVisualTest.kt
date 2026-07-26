package com.worklogai.app.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import org.junit.Rule
import org.junit.Test

class WorkLogNavigationVisualTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactLayoutUsesUnifiedBottomNavigation() {
        composeRule.setContent {
            WorkLogTheme(dynamicColor = false) {
                WorkLogBottomBar(
                    visible = true,
                    currentDestination = null,
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("历史").assertIsDisplayed()
        composeRule.onNodeWithText("总结").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun wideLayoutUsesUnifiedNavigationRail() {
        composeRule.setContent {
            WorkLogTheme(darkTheme = true, dynamicColor = false) {
                WorkLogNavigationRail(
                    visible = true,
                    currentDestination = null,
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("navigation_rail").assertIsDisplayed()
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }
}
