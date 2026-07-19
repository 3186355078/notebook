package com.worklogai.app.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.ai.skill.worksummary.SummaryItem
import com.worklogai.app.ai.skill.worksummary.WorkSummaryResult
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkillRequest
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_END
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_START
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_TYPE
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceSummaryUiIntegrationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun weeklyAndMonthlyRestoreOriginalPersistWhileCopyUsesTheSystemClipboard() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val backup = ByteArrayOutputStream()
            assertTrue(dependencies.backupArchiveService().createBackup(backup) is BackupOperationResult.Success)
            val clipboard = context.getSystemService(ClipboardManager::class.java)

            try {
                val weeklyOriginal = insertSummary(dependencies, SummaryType.WEEKLY, WEEKLY_PERIOD)
                verifyRestoreAndCopy(context, dependencies, SummaryType.WEEKLY, WEEKLY_PERIOD, weeklyOriginal)

                val monthlyOriginal = insertSummary(dependencies, SummaryType.MONTHLY, MONTHLY_PERIOD)
                verifyRestoreAndCopy(context, dependencies, SummaryType.MONTHLY, MONTHLY_PERIOD, monthlyOriginal)
            } finally {
                clipboard.clearPrimaryClip()
                assertTrue(
                    dependencies.backupArchiveService().restoreBackup(
                        ByteArrayInputStream(backup.toByteArray()),
                    ) is BackupOperationResult.Success,
                )
            }
        }

    private suspend fun insertSummary(
        dependencies: Stage9TestEntryPoint,
        type: SummaryType,
        period: DateRange,
    ): String {
        dependencies.database().workSummaryDao().getByPeriod(type, period.start, period.end)?.let {
            dependencies.database().workSummaryDao().delete(it)
        }
        val result =
            WorkSummaryResult(
                title = if (type == SummaryType.WEEKLY) "Controlled weekly report" else "Controlled monthly report",
                overview = "Controlled original overview",
                completedItems = listOf(SummaryItem("Controlled completed item", listOf(period.start.toString()))),
            )
        val originalJson = SUMMARY_JSON.encodeToString(result)
        val formatted =
            dependencies.workSummarySkill().format(
                dependencies
                    .workSummarySkill()
                    .parse(
                        originalJson,
                        WorkSummarySkillRequest(type, period.start, period.end, "zh-CN"),
                    ).getOrThrow(),
            )
        dependencies.database().workSummaryDao().insert(
            WorkSummaryEntity(
                id = "stage9-ui-${type.name.lowercase()}",
                summaryType = type,
                periodStart = period.start,
                periodEnd = period.end,
                status = SummaryStatus.SUCCESS,
                sourceHash = null,
                aiProvider = "mock",
                modelName = "mock",
                originalContent = originalJson,
                editedContent = MANUAL_EDIT,
                errorMessage = null,
                createdAt = NOW,
                updatedAt = NOW,
                generatedAt = NOW,
            ),
        )
        return formatted
    }

    private suspend fun verifyRestoreAndCopy(
        context: Context,
        dependencies: Stage9TestEntryPoint,
        type: SummaryType,
        period: DateRange,
        formatted: String,
    ) {
        ActivityScenario.launch<MainActivity>(summaryIntent(context, type, period)).use {
            composeRule.waitForText(MANUAL_EDIT)
            composeRule.onNodeWithText("恢复 AI 原始版本").performClick()
            composeRule.onNode(hasText("取消") and hasClickAction()).performClick()
            composeRule.onNodeWithText(MANUAL_EDIT).assertIsDisplayed()

            composeRule.onNodeWithText("恢复 AI 原始版本").performClick()
            composeRule.onNode(hasText("恢复") and hasClickAction()).performClick()
            composeRule.waitForText(formatted)
            composeRule.onNodeWithText(formatted).assertIsDisplayed()

            composeRule.onNodeWithText("复制").performClick()
            composeRule.waitForText("已复制")
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val copied =
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
            assertEquals(formatted, copied)
        }

        val persisted = dependencies.database().workSummaryDao().getByPeriod(type, period.start, period.end)
        assertEquals(formatted, persisted?.editedContent)
        ActivityScenario.launch<MainActivity>(summaryIntent(context, type, period)).use {
            composeRule.waitForText(formatted)
            composeRule.onNodeWithText(formatted).assertIsDisplayed()
        }
    }

    private fun summaryIntent(
        context: Context,
        type: SummaryType,
        period: DateRange,
    ): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SUMMARY_TYPE, type.name)
            .putExtra(EXTRA_SUMMARY_PERIOD_START, period.start.toString())
            .putExtra(EXTRA_SUMMARY_PERIOD_END, period.end.toString())

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitForText(value: String) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val MANUAL_EDIT = "Stage 9 manual summary edit"
        const val UI_TIMEOUT_MILLIS = 15_000L
        val NOW: Instant = Instant.parse("2026-07-13T00:00:00Z")
        val WEEKLY_PERIOD = DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12))
        val MONTHLY_PERIOD = DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))
        val SUMMARY_JSON = Json { encodeDefaults = true }
    }
}
