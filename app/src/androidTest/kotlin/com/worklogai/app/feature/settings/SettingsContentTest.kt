package com.worklogai.app.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.worklogai.app.core.datastore.AiSettings
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSecureAiConfigurationControls() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state =
                        AiSettingsUiState(
                            settings = AiSettings(useMockProvider = false),
                            hasApiKey = true,
                            isLoading = false,
                        ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("大模型服务").assertIsDisplayed()
        composeRule.onNodeWithText("保存设置").assertIsDisplayed()
        composeRule.onNodeWithText("删除 API Key").assertIsDisplayed()
    }
}
