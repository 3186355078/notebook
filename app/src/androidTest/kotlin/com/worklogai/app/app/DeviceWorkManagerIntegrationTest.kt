package com.worklogai.app.app

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.worklogai.app.core.autosummary.AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME
import com.worklogai.app.core.autosummary.AutoSummaryPeriod
import com.worklogai.app.core.autosummary.AutoSummaryWorkRequestFactory
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.worker.AutoSummaryCheckWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeviceWorkManagerIntegrationTest {
    @Test
    fun generationConstraintsMatchMockConnectedAndUnmeteredPoliciesOnDevice() {
        val factory = AutoSummaryWorkRequestFactory()
        val period =
            AutoSummaryPeriod(
                SummaryType.WEEKLY,
                DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)),
            )

        val mock = factory.generate(period, AiSettings(useMockProvider = true)).workSpec.constraints
        val connected =
            factory
                .generate(period, AiSettings(useMockProvider = false, allowMobileNetwork = true))
                .workSpec.constraints
        val unmetered =
            factory
                .generate(period, AiSettings(useMockProvider = false, allowMobileNetwork = false))
                .workSpec.constraints

        assertEquals(NetworkType.NOT_REQUIRED, mock.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, connected.requiredNetworkType)
        assertEquals(NetworkType.UNMETERED, unmetered.requiredNetworkType)
        listOf(mock, connected, unmetered).forEach { constraints ->
            assertTrue(constraints.requiresBatteryNotLow())
            assertTrue(constraints.requiresStorageNotLow())
        }
    }

    @Test
    fun platformWorkManagerKeepsOnePeriodicRequestForAUniqueName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workManager = WorkManager.getInstance(context)
        val name = "stage9-device-unique-periodic"
        val first = delayedPeriodicRequest()
        val second = delayedPeriodicRequest()

        try {
            workManager
                .enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, first)
                .result
                .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            workManager
                .enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, second)
                .result
                .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            val workInfos =
                workManager
                    .getWorkInfosForUniqueWork(name)
                    .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, workInfos.size)
        } finally {
            workManager.cancelUniqueWork(name).result.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Test
    fun prepareProductionScheduleForDeviceRebootVerification() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val entryPoint = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val repository = entryPoint.settingsRepository()
            val original = repository.getSettings()
            val preferences = context.getSharedPreferences(REBOOT_FIXTURE_PREFERENCES, 0)
            val editor = preferences.edit()
            if (!preferences.contains(ORIGINAL_WEEKLY_KEY)) {
                val arguments = InstrumentationRegistry.getArguments()
                editor.putBoolean(
                    ORIGINAL_WEEKLY_KEY,
                    arguments.getString(ORIGINAL_WEEKLY_ARGUMENT)?.toBooleanStrictOrNull()
                        ?: original.autoWeeklySummaryEnabled,
                )
                editor.putBoolean(
                    ORIGINAL_MONTHLY_KEY,
                    arguments.getString(ORIGINAL_MONTHLY_ARGUMENT)?.toBooleanStrictOrNull()
                        ?: original.autoMonthlySummaryEnabled,
                )
            }
            assertTrue(editor.putLong(ELAPSED_REALTIME_KEY, SystemClock.elapsedRealtime()).commit())
            val enabled =
                original.copy(
                    autoWeeklySummaryEnabled = true,
                    autoMonthlySummaryEnabled = true,
                    autoSummaryConsentAcknowledged = true,
                )

            repository.saveSettings(enabled).getOrThrow()
            entryPoint.autoSummaryScheduler().applySettings(enabled, enqueueImmediateCheck = false).getOrThrow()
            awaitActiveWorkCount(AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME, expected = 1)
        }

    @Test
    fun verifyProductionScheduleAfterDeviceRebootAndRestoreSettings() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val entryPoint = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val preferences = context.getSharedPreferences(REBOOT_FIXTURE_PREFERENCES, 0)
            if (!preferences.contains(ELAPSED_REALTIME_KEY)) {
                val original = entryPoint.settingsRepository().getSettings()
                preferences
                    .edit()
                    .putBoolean(ORIGINAL_WEEKLY_KEY, original.autoWeeklySummaryEnabled)
                    .putBoolean(ORIGINAL_MONTHLY_KEY, original.autoMonthlySummaryEnabled)
                    .putLong(ELAPSED_REALTIME_KEY, SystemClock.elapsedRealtime())
                    .commit()
                val enabled =
                    original.copy(
                        autoWeeklySummaryEnabled = true,
                        autoMonthlySummaryEnabled = true,
                        autoSummaryConsentAcknowledged = true,
                    )
                entryPoint.settingsRepository().saveSettings(enabled).getOrThrow()
                entryPoint.autoSummaryScheduler().applySettings(enabled, enqueueImmediateCheck = false).getOrThrow()
            }
            val preparedElapsedRealtime =
                preferences.getLong(ELAPSED_REALTIME_KEY, SystemClock.elapsedRealtime())
            val settings = entryPoint.settingsRepository().getSettings()

            assertTrue(settings.autoWeeklySummaryEnabled)
            assertTrue(settings.autoMonthlySummaryEnabled)
            val requireReboot =
                InstrumentationRegistry
                    .getArguments()
                    .getString(REQUIRE_REBOOT_ARGUMENT)
                    .toBoolean()
            if (requireReboot) {
                assertTrue(SystemClock.elapsedRealtime() < preparedElapsedRealtime)
            } else {
                assertTrue(SystemClock.elapsedRealtime() >= preparedElapsedRealtime)
            }
            entryPoint.autoSummaryScheduler().applySettings(settings, enqueueImmediateCheck = false).getOrThrow()
            entryPoint.autoSummaryScheduler().applySettings(settings, enqueueImmediateCheck = false).getOrThrow()
            awaitActiveWorkCount(AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME, expected = 1)

            val restored =
                settings.copy(
                    autoWeeklySummaryEnabled = preferences.getBoolean(ORIGINAL_WEEKLY_KEY, false),
                    autoMonthlySummaryEnabled = preferences.getBoolean(ORIGINAL_MONTHLY_KEY, false),
                )
            entryPoint.settingsRepository().saveSettings(restored).getOrThrow()
            entryPoint.autoSummaryScheduler().applySettings(restored, enqueueImmediateCheck = false).getOrThrow()
            val persisted = entryPoint.settingsRepository().getSettings()
            assertEquals(restored.autoWeeklySummaryEnabled, persisted.autoWeeklySummaryEnabled)
            assertEquals(restored.autoMonthlySummaryEnabled, persisted.autoMonthlySummaryEnabled)
            val expectedPeriodicCount =
                if (restored.autoWeeklySummaryEnabled || restored.autoMonthlySummaryEnabled) 1 else 0
            awaitActiveWorkCount(AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME, expectedPeriodicCount)
            assertTrue(preferences.edit().clear().commit())
        }

    private fun awaitActiveWorkCount(
        name: String,
        expected: Int,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workManager = WorkManager.getInstance(context)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FUTURE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val activeCount =
                workManager
                    .getWorkInfosForUniqueWork(name)
                    .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .count { workInfo -> !workInfo.state.isFinished }
            if (activeCount == expected) return
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        val activeCount =
            workManager
                .getWorkInfosForUniqueWork(name)
                .get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .count { workInfo -> !workInfo.state.isFinished }
        assertEquals(expected, activeCount)
    }

    private fun delayedPeriodicRequest() =
        PeriodicWorkRequestBuilder<AutoSummaryCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()

    private companion object {
        const val FUTURE_TIMEOUT_SECONDS = 20L
        const val POLL_INTERVAL_MILLIS = 50L
        const val REBOOT_FIXTURE_PREFERENCES = "stage9-reboot-fixture"
        const val ORIGINAL_WEEKLY_KEY = "original-weekly"
        const val ORIGINAL_MONTHLY_KEY = "original-monthly"
        const val ELAPSED_REALTIME_KEY = "elapsed-realtime"
        const val REQUIRE_REBOOT_ARGUMENT = "requireReboot"
        const val ORIGINAL_WEEKLY_ARGUMENT = "originalWeekly"
        const val ORIGINAL_MONTHLY_ARGUMENT = "originalMonthly"
    }
}
