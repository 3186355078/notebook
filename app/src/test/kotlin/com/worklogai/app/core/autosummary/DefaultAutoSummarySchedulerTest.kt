package com.worklogai.app.core.autosummary

import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DefaultAutoSummarySchedulerTest {
    @Test
    fun `enabling either type schedules one periodic and one immediate check`() =
        runBlocking {
            val gateway = FakeWorkGateway()
            val scheduler = DefaultAutoSummaryScheduler(gateway, AutoSummaryWorkRequestFactory())

            scheduler.applySettings(AiSettings(autoWeeklySummaryEnabled = true), enqueueImmediateCheck = true)
            scheduler.applySettings(AiSettings(autoMonthlySummaryEnabled = true), enqueueImmediateCheck = true)

            assertEquals(2, gateway.periodicChecks.size)
            assertEquals(2, gateway.immediateChecks.size)
            assertTrue(gateway.periodicChecks.all { AUTO_SUMMARY_TAG in it.tags })
            assertTrue(gateway.periodicChecks.all { AUTO_SUMMARY_CHECK_TAG in it.tags })
        }

    @Test
    fun `disabling every type cancels only automatic work`() =
        runBlocking {
            val gateway = FakeWorkGateway()
            val scheduler = DefaultAutoSummaryScheduler(gateway, AutoSummaryWorkRequestFactory())

            scheduler.applySettings(AiSettings(), enqueueImmediateCheck = false)

            assertTrue(AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME in gateway.cancelledUniqueNames)
            assertTrue(AUTO_SUMMARY_IMMEDIATE_CHECK_WORK_NAME in gateway.cancelledUniqueNames)
            assertEquals(listOf(AUTO_SUMMARY_GENERATE_TAG), gateway.cancelledTags)
        }

    @Test
    fun `generation request has stable name safe input and mock network exemption`() =
        runBlocking {
            val gateway = FakeWorkGateway()
            val scheduler = DefaultAutoSummaryScheduler(gateway, AutoSummaryWorkRequestFactory())
            val period = period(SummaryType.WEEKLY, "2026-07-13", "2026-07-19")

            scheduler.enqueueGeneration(period, AiSettings(useMockProvider = true))

            val captured = gateway.generations.single()
            assertEquals("auto-summary-generate-weekly-2026-07-13-2026-07-19-v1", captured.first)
            assertEquals(
                AUTO_TRIGGER,
                captured.second.workSpec.input
                    .getString(INPUT_TRIGGER),
            )
            assertEquals(
                SummaryType.WEEKLY.name,
                captured.second.workSpec.input
                    .getString(INPUT_SUMMARY_TYPE),
            )
            assertFalse(
                captured.second.workSpec.input.keyValueMap.keys
                    .any { it.contains("key", ignoreCase = true) },
            )
            assertNotNull(captured.second)
        }

    @Test
    fun `real provider generation tags the type and uses a distinct request`() =
        runBlocking {
            val gateway = FakeWorkGateway()
            val scheduler = DefaultAutoSummaryScheduler(gateway, AutoSummaryWorkRequestFactory())

            scheduler.enqueueGeneration(
                period(SummaryType.MONTHLY, "2026-06-01", "2026-06-30"),
                AiSettings(useMockProvider = false, allowMobileNetwork = false),
            )

            val request = gateway.generations.single().second
            assertTrue(AUTO_SUMMARY_GENERATE_TAG in request.tags)
            assertTrue(typeTag(SummaryType.MONTHLY) in request.tags)
            assertEquals("UNMETERED", request.workSpec.constraints.requiredNetworkType.name)
        }

    @Test
    fun `cancelling one type preserves the other type tag`() =
        runBlocking {
            val gateway = FakeWorkGateway()
            val scheduler = DefaultAutoSummaryScheduler(gateway, AutoSummaryWorkRequestFactory())

            scheduler.cancelAutomaticGeneration(SummaryType.WEEKLY)

            assertEquals(listOf(typeTag(SummaryType.WEEKLY)), gateway.cancelledTags)
        }

    private fun period(
        type: SummaryType,
        start: String,
        end: String,
    ) = AutoSummaryPeriod(type, DateRange(LocalDate.parse(start), LocalDate.parse(end)))
}

private class FakeWorkGateway : AutoSummaryWorkGateway {
    val periodicChecks = mutableListOf<PeriodicWorkRequest>()
    val immediateChecks = mutableListOf<OneTimeWorkRequest>()
    val generations = mutableListOf<Pair<String, OneTimeWorkRequest>>()
    val cancelledUniqueNames = mutableListOf<String>()
    val cancelledTags = mutableListOf<String>()

    override fun enqueueUniquePeriodicCheck(request: PeriodicWorkRequest) {
        periodicChecks += request
    }

    override fun enqueueUniqueImmediateCheck(request: OneTimeWorkRequest) {
        immediateChecks += request
    }

    override fun enqueueUniqueGeneration(
        name: String,
        request: OneTimeWorkRequest,
    ) {
        generations += name to request
    }

    override fun cancelUniqueWork(name: String) {
        cancelledUniqueNames += name
    }

    override fun cancelAllByTag(tag: String) {
        cancelledTags += tag
    }
}
