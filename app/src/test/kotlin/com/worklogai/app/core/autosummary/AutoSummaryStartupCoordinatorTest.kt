package com.worklogai.app.core.autosummary

import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AutoSummaryStartupCoordinatorTest {
    @Test
    fun `version change reconciles settings with an immediate check and records the version`() =
        runBlocking {
            val stateRepository = StartupStateRepository(AutoSummaryScheduleState(schedulerVersion = 0))
            val scheduler = StartupScheduler()
            val coordinator =
                AutoSummaryStartupCoordinator(
                    settingsRepository = StartupSettingsRepository(AiSettings(autoWeeklySummaryEnabled = true)),
                    scheduler = scheduler,
                    scheduleStateRepository = stateRepository,
                    dispatcher = Dispatchers.Unconfined,
                )

            coordinator.reconcile()

            assertEquals(1, scheduler.applyCalls.size)
            assertTrue(scheduler.applyCalls.single().second)
            assertEquals(AUTO_SUMMARY_SCHEDULER_VERSION, stateRepository.updatedVersion)
        }

    @Test
    fun `scheduler failure leaves an old version untouched for the next startup retry`() =
        runBlocking {
            val stateRepository = StartupStateRepository(AutoSummaryScheduleState(schedulerVersion = 0))
            val scheduler = StartupScheduler(applyResult = Result.failure(AutoSummarySchedulingException()))
            val coordinator =
                AutoSummaryStartupCoordinator(
                    settingsRepository = StartupSettingsRepository(AiSettings(autoMonthlySummaryEnabled = true)),
                    scheduler = scheduler,
                    scheduleStateRepository = stateRepository,
                    dispatcher = Dispatchers.Unconfined,
                )

            coordinator.reconcile()

            assertTrue(scheduler.applyCalls.single().second)
            assertFalse(stateRepository.updated)
        }
}

private class StartupSettingsRepository(
    private val value: AiSettings,
) : AiSettingsRepository {
    override val settings: Flow<AiSettings> = MutableStateFlow(value)

    override suspend fun getSettings(): AiSettings = value

    override suspend fun saveSettings(settings: AiSettings): Result<Unit> = Result.success(Unit)
}

private class StartupStateRepository(
    value: AutoSummaryScheduleState,
) : AutoSummaryScheduleStateRepository {
    override val state = MutableStateFlow(value)
    var updated = false
    var updatedVersion: Int? = null

    override suspend fun getState(): Result<AutoSummaryScheduleState> = Result.success(state.value)

    override suspend fun updateLastCheck(time: Instant): Result<Unit> = Result.success(Unit)

    override suspend fun markEvaluated(
        type: SummaryType,
        periodEnd: LocalDate,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun resetBaseline(type: SummaryType): Result<Unit> = Result.success(Unit)

    override suspend fun updateSchedulerVersion(version: Int): Result<Unit> {
        updated = true
        updatedVersion = version
        return Result.success(Unit)
    }
}

private class StartupScheduler(
    private val applyResult: Result<Unit> = Result.success(Unit),
) : AutoSummaryScheduler {
    val applyCalls = mutableListOf<Pair<AiSettings, Boolean>>()

    override suspend fun applySettings(
        settings: AiSettings,
        enqueueImmediateCheck: Boolean,
    ): Result<Unit> {
        applyCalls += settings to enqueueImmediateCheck
        return applyResult
    }

    override suspend fun enqueueImmediateCheck(): Result<Unit> = Result.success(Unit)

    override suspend fun enqueueGeneration(
        period: AutoSummaryPeriod,
        settings: AiSettings,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun cancelAutomaticGeneration(type: SummaryType?): Result<Unit> = Result.success(Unit)
}
