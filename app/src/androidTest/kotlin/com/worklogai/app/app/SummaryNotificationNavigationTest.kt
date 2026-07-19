package com.worklogai.app.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_END
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_START
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_TYPE
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SummaryNotificationNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun weeklyNotificationDeepLinkOpensTheCompletedWeekOnColdActivityStart() {
        ActivityScenario.launch<MainActivity>(summaryIntent("WEEKLY", "2026-07-06", "2026-07-12")).use {
            composeRule.waitForText(WEEKLY_LABEL)
            composeRule.onNodeWithText(WEEKLY_LABEL).assertIsDisplayed()
        }
    }

    @Test
    fun monthlyNotificationDeepLinkIsConsumedWhenActivityWasInBackground() {
        ActivityScenario.launch<MainActivity>(baseIntent()).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            context.startActivity(
                summaryIntent("MONTHLY", "2026-06-01", "2026-06-30")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )

            composeRule.waitForText(MONTHLY_LABEL)
            composeRule.onNodeWithText(MONTHLY_LABEL).assertIsDisplayed()
        }
    }

    @Test
    fun missingMalformedAndFutureDeepLinksFallBackWithoutOpeningAPeriod() {
        val invalidIntents =
            listOf(
                summaryIntent(null, null, null),
                summaryIntent("WEEKLY", "not-a-date", "2026-07-12"),
                summaryIntent("WEEKLY", "2099-01-05", "2099-01-11"),
            )

        invalidIntents.forEach { intent ->
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitForText("今天")
                assertTrue(composeRule.onAllNodes(hasText("今天")).fetchSemanticsNodes().isNotEmpty())
                assertTrue(composeRule.onAllNodes(hasText(WEEKLY_LABEL)).fetchSemanticsNodes().isEmpty())
            }
        }
    }

    private fun baseIntent(): Intent = Intent(context, MainActivity::class.java)

    private fun summaryIntent(
        type: String?,
        start: String?,
        end: String?,
    ): Intent =
        baseIntent().apply {
            type?.let { putExtra(EXTRA_SUMMARY_TYPE, it) }
            start?.let { putExtra(EXTRA_SUMMARY_PERIOD_START, it) }
            end?.let { putExtra(EXTRA_SUMMARY_PERIOD_END, it) }
        }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitForText(value: String) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        val context: Context = ApplicationProvider.getApplicationContext()
        const val WEEKLY_LABEL = "2026年7月6日—12日"
        const val MONTHLY_LABEL = "2026年6月1日—30日"
        const val UI_TIMEOUT_MILLIS = 15_000L
    }
}
