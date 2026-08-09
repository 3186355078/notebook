package com.worklogai.app.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class WorkLogThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightThemeUsesNeutralBrandSurfaceHierarchy() {
        var container = Color.Unspecified
        var selected = Color.Unspecified

        composeRule.setContent {
            WorkLogTheme(darkTheme = false, dynamicColor = false) {
                container = MaterialTheme.colorScheme.surfaceContainer
                selected = MaterialTheme.colorScheme.secondaryContainer
            }
        }

        composeRule.runOnIdle {
            assertEquals(LightSurfaceContainer, container)
            assertEquals(LightSecondaryContainer, selected)
            assertNotEquals(Color(0xFFDDE1FF), selected)
        }
    }

    @Test
    fun darkThemeUsesNeutralDeepSurfaceHierarchy() {
        var low = Color.Unspecified
        var high = Color.Unspecified

        composeRule.setContent {
            WorkLogTheme(darkTheme = true, dynamicColor = false) {
                low = MaterialTheme.colorScheme.surfaceContainerLow
                high = MaterialTheme.colorScheme.surfaceContainerHigh
            }
        }

        composeRule.runOnIdle {
            assertEquals(DarkSurfaceContainerLow, low)
            assertEquals(DarkSurfaceContainerHigh, high)
            assertNotEquals(low, high)
        }
    }
}
