package com.worklogai.app.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.worklogai.app.core.datastore.AiSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        composeRule.onNodeWithText("AI 服务").assertIsDisplayed()
        composeRule.onNodeWithText("保存设置").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("删除 API Key").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun largeFontThemeSummaryIsNotEllipsized() {
        val summary = "主题跟随系统；优先级同时使用文字、图标与局部颜色区分"

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
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
        }

        val layoutResults = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(summary).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue(action(layoutResults))
        }
        composeRule.runOnIdle {
            assertTrue(layoutResults.isNotEmpty())
            assertFalse(layoutResults.single().isLineEllipsized(layoutResults.single().lineCount - 1))
        }
    }
}
