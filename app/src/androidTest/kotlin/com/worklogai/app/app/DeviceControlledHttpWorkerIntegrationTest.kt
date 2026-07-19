package com.worklogai.app.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.worklogai.app.core.autosummary.AUTO_TRIGGER
import com.worklogai.app.core.autosummary.INPUT_PERIOD_END
import com.worklogai.app.core.autosummary.INPUT_PERIOD_START
import com.worklogai.app.core.autosummary.INPUT_SUMMARY_TYPE
import com.worklogai.app.core.autosummary.INPUT_TRIGGER
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.worker.AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID
import com.worklogai.app.worker.GenerateSummaryWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeviceControlledHttpWorkerIntegrationTest {
    @Test
    fun transient429RetriesToTheLimitWhile500AndTimeoutRecover() =
        runBlocking {
            withFixture { fixture ->
                fixture.resetServer()
                fixture.prepareScenario("test-429")
                val rateLimited = fixture.enqueue()
                fixture.awaitRetry(rateLimited.id)
                assertFalse(fixture.activeNotificationTitles().any { it.contains("失败") })
                val rateLimitResult = fixture.awaitFinished(rateLimited.id)
                assertEquals(WorkInfo.State.FAILED, rateLimitResult.state)
                assertEquals(3, rateLimitResult.runAttemptCount)
                assertEquals(3, fixture.requestCount("test-429"))
                assertEquals(1, fixture.activeNotificationTitles().count { it.contains("失败") })
                assertEquals(OLD_EDITED_CONTENT, fixture.currentSummary()?.editedContent)

                fixture.prepareScenario("test-500-recover")
                val serverError = fixture.awaitFinished(fixture.enqueue().id)
                assertEquals(WorkInfo.State.SUCCEEDED, serverError.state)
                assertEquals(2, serverError.runAttemptCount)
                assertEquals(2, fixture.requestCount("test-500-recover"))

                fixture.prepareScenario("test-timeout-recover")
                val timeout = fixture.awaitFinished(fixture.enqueue().id)
                assertEquals(WorkInfo.State.SUCCEEDED, timeout.state)
                assertEquals(2, timeout.runAttemptCount)
                assertEquals(2, fixture.requestCount("test-timeout-recover"))
            }
        }

    @Test
    fun permanentHttpErrorsAndInvalidJsonDoNotEnterWorkManagerRetryLoops() =
        runBlocking {
            withFixture { fixture ->
                fixture.resetServer()
                listOf("test-401", "test-403", "test-model-not-found").forEach { scenario ->
                    fixture.prepareScenario(scenario)
                    val result = fixture.awaitFinished(fixture.enqueue().id)
                    assertEquals(WorkInfo.State.FAILED, result.state)
                    assertEquals(1, result.runAttemptCount)
                    assertEquals(1, fixture.requestCount(scenario))
                    assertEquals(OLD_EDITED_CONTENT, fixture.currentSummary()?.editedContent)
                }

                fixture.prepareScenario("test-invalid-json")
                val invalid = fixture.awaitFinished(fixture.enqueue().id)
                assertEquals(WorkInfo.State.FAILED, invalid.state)
                assertEquals(1, invalid.runAttemptCount)
                assertEquals(2, fixture.requestCount("test-invalid-json"))

                fixture.prepareScenario("test-repair-invalid-json")
                val repaired = fixture.awaitFinished(fixture.enqueue().id)
                assertEquals(WorkInfo.State.SUCCEEDED, repaired.state)
                assertEquals(1, repaired.runAttemptCount)
                assertEquals(2, fixture.requestCount("test-repair-invalid-json"))
            }
        }

    @Test
    fun disabledAutomaticSummarySkipsTheProviderWithoutFailureNotification() =
        runBlocking {
            withFixture { fixture ->
                fixture.resetServer()
                fixture.prepareScenario("test-success", enabled = false)

                val result = fixture.awaitFinished(fixture.enqueue().id)

                assertEquals(WorkInfo.State.SUCCEEDED, result.state)
                assertEquals(0, fixture.requestCount("test-success"))
                assertNull(fixture.currentSummary())
                assertTrue(fixture.activeNotificationTitles().isEmpty())
            }
        }

    private suspend fun withFixture(block: suspend (ControlledWorkerFixture) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
        assertFalse(dependencies.secretStore().hasApiKey())
        val backup = ByteArrayOutputStream()
        assertTrue(dependencies.backupArchiveService().createBackup(backup) is BackupOperationResult.Success)
        val fixture = ControlledWorkerFixture(dependencies)
        try {
            fixture.seedEntry()
            dependencies.secretStore().saveApiKey("not-a-real-stage9-key").getOrThrow()
            block(fixture)
        } finally {
            fixture.cleanupWork()
            fixture.notificationManager.cancelAll()
            dependencies.secretStore().deleteApiKey().getOrThrow()
            assertTrue(
                dependencies.backupArchiveService().restoreBackup(
                    ByteArrayInputStream(backup.toByteArray()),
                ) is BackupOperationResult.Success,
            )
        }
    }

    private class ControlledWorkerFixture(
        private val dependencies: Stage9TestEntryPoint,
    ) {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val workManager = WorkManager.getInstance(context)
        val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)

        suspend fun seedEntry() {
            dependencies.database().workEntryDao().getWithContentByDateIncludingDeleted(ENTRY_DATE)?.entry?.let {
                dependencies.database().workEntryDao().delete(it)
            }
            dependencies.database().workEntryDao().insert(
                WorkEntryEntity(
                    id = ENTRY_ID,
                    entryDate = ENTRY_DATE,
                    title = "Stage 9 controlled HTTP fixture",
                    allowAiProcessing = true,
                    isDeleted = false,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            )
            dependencies.database().contentBlockDao().insert(
                ContentBlockEntity(
                    id = BLOCK_ID,
                    entryId = ENTRY_ID,
                    blockType = ContentBlockType.TEXT,
                    blockOrder = 0,
                    textContent = "Non-sensitive controlled Worker input",
                    structuredContent = null,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            )
        }

        suspend fun prepareScenario(
            scenario: String,
            enabled: Boolean = true,
        ) {
            cleanupWork()
            notificationManager.cancelAll()
            currentSummary()?.let { dependencies.database().workSummaryDao().delete(it) }
            if (enabled) dependencies.database().workSummaryDao().insert(pendingSummary())
            dependencies
                .settingsRepository()
                .saveSettings(
                    AiSettings(
                        baseUrl = CONTROLLED_BASE_URL,
                        model = scenario,
                        timeoutSeconds = 10,
                        useMockProvider = false,
                        allowMobileNetwork = true,
                        autoWeeklySummaryEnabled = enabled,
                        notifyOnAutoSummaryCompletion = true,
                        autoSummaryConsentAcknowledged = true,
                    ),
                ).getOrThrow()
        }

        fun enqueue() =
            OneTimeWorkRequestBuilder<GenerateSummaryWorker>()
                .setInputData(
                    Data
                        .Builder()
                        .putString(INPUT_SUMMARY_TYPE, SummaryType.WEEKLY.name)
                        .putString(INPUT_PERIOD_START, PERIOD_START.toString())
                        .putString(INPUT_PERIOD_END, PERIOD_END.toString())
                        .putString(INPUT_TRIGGER, AUTO_TRIGGER)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(TEST_TAG)
                .build()
                .also { request -> workManager.enqueue(request).result.get(10, TimeUnit.SECONDS) }

        fun awaitRetry(id: java.util.UUID): WorkInfo =
            awaitWork(id, 30) { info -> info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount >= 1 }

        fun awaitFinished(id: java.util.UUID): WorkInfo = awaitWork(id, 90) { info -> info.state.isFinished }

        suspend fun currentSummary(): WorkSummaryEntity? =
            dependencies.database().workSummaryDao().getByPeriod(SummaryType.WEEKLY, PERIOD_START, PERIOD_END)

        fun activeNotificationTitles(): List<String> =
            notificationManager.activeNotifications
                .filter { it.notification.channelId == AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID }
                .mapNotNull { notification ->
                    notification.notification.extras
                        .getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString()
                }

        fun resetServer() {
            open("/__reset", method = "POST")
        }

        fun requestCount(scenario: String): Int = JSONObject(open("/__status")).optInt(scenario, 0)

        fun cleanupWork() {
            workManager.cancelAllWorkByTag(TEST_TAG).result.get(10, TimeUnit.SECONDS)
        }

        private fun awaitWork(
            id: java.util.UUID,
            timeoutSeconds: Long,
            predicate: (WorkInfo) -> Boolean,
        ): WorkInfo {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (System.nanoTime() < deadline) {
                val info = requireNotNull(workManager.getWorkInfoById(id).get(10, TimeUnit.SECONDS))
                if (predicate(info)) return info
                Thread.sleep(100)
            }
            return requireNotNull(workManager.getWorkInfoById(id).get(10, TimeUnit.SECONDS)).also {
                assertTrue("WorkInfo did not reach the expected state: $it", predicate(it))
            }
        }

        private fun open(
            path: String,
            method: String = "GET",
        ): String {
            val connection = URL("$CONTROLLED_BASE_URL$path").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            if (method == "POST") connection.doOutput = true
            return try {
                val code = connection.responseCode
                if (code ==
                    HttpURLConnection.HTTP_NO_CONTENT
                ) {
                    ""
                } else {
                    connection.inputStream.bufferedReader().readText()
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun pendingSummary() =
            WorkSummaryEntity(
                id = SUMMARY_ID,
                summaryType = SummaryType.WEEKLY,
                periodStart = PERIOD_START,
                periodEnd = PERIOD_END,
                status = SummaryStatus.PENDING,
                sourceHash = "stage9-old-source",
                aiProvider = "mock",
                modelName = "mock",
                originalContent = "{}",
                editedContent = OLD_EDITED_CONTENT,
                errorMessage = null,
                createdAt = NOW,
                updatedAt = NOW,
                generatedAt = NOW,
            )
    }

    private companion object {
        const val CONTROLLED_BASE_URL = "http://127.0.0.1:18080"
        const val TEST_TAG = "stage9-controlled-http"
        const val ENTRY_ID = "stage9-controlled-http-entry"
        const val BLOCK_ID = "stage9-controlled-http-block"
        const val SUMMARY_ID = "stage9-controlled-http-summary"
        const val OLD_EDITED_CONTENT = "Stage 9 old summary remains available"
        val PERIOD_START: LocalDate = LocalDate.of(2026, 7, 6)
        val PERIOD_END: LocalDate = LocalDate.of(2026, 7, 12)
        val ENTRY_DATE: LocalDate = PERIOD_START
        val NOW: Instant = Instant.parse("2026-07-13T00:00:00Z")
    }
}
