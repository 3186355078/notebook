package com.worklogai.app.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.worklogai.app.ai.usecase.GenerateWorkSummaryResult
import com.worklogai.app.ai.usecase.GenerateWorkSummaryUseCase
import com.worklogai.app.ai.usecase.SummaryGenerationMode
import com.worklogai.app.core.autosummary.AUTO_TRIGGER
import com.worklogai.app.core.autosummary.AutoSummaryDuePeriodResolver
import com.worklogai.app.core.autosummary.AutoSummaryPeriod
import com.worklogai.app.core.autosummary.AutoSummaryScheduleState
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.autosummary.INPUT_PERIOD_END
import com.worklogai.app.core.autosummary.INPUT_PERIOD_START
import com.worklogai.app.core.autosummary.INPUT_SUMMARY_TYPE
import com.worklogai.app.core.autosummary.INPUT_TRIGGER
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkSummaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutoSummaryCheckWorkerTest {
    @Test
    fun `disabled automatic settings complete without enqueuing work`() =
        runBlocking {
            val scheduler = mockk<AutoSummaryScheduler>(relaxed = true)
            val worker = checkWorker(settings = AiSettings(), scheduler = scheduler)

            assertSuccess(worker.doWork())
            coVerify(exactly = 0) { scheduler.enqueueGeneration(any(), any()) }
        }

    @Test
    fun `due pending period enqueues generation but does not advance boundary before completion`() =
        runBlocking {
            val period = weeklyPeriod()
            val scheduler = mockk<AutoSummaryScheduler>()
            coEvery { scheduler.enqueueGeneration(period, any()) } returns Result.success(Unit)
            val stateRepository = mockScheduleStateRepository()
            val summaryRepository = mockk<WorkSummaryRepository>()
            coEvery { summaryRepository.getSummary(any(), any(), any()) } returns DataResult.Success(null)
            val worker =
                checkWorker(
                    settings = AiSettings(autoWeeklySummaryEnabled = true),
                    duePeriods = listOf(period),
                    scheduler = scheduler,
                    stateRepository = stateRepository,
                    summaryRepository = summaryRepository,
                )

            assertSuccess(worker.doWork())
            coVerify(exactly = 1) { scheduler.enqueueGeneration(period, any()) }
            coVerify(exactly = 0) { stateRepository.markEvaluated(any(), any()) }
            coVerify(exactly = 1) { stateRepository.updateLastCheck(any()) }
        }

    @Test
    fun `existing success is marked evaluated without another generation`() =
        runBlocking {
            val period = weeklyPeriod()
            val scheduler = mockk<AutoSummaryScheduler>(relaxed = true)
            val stateRepository = mockScheduleStateRepository()
            val summaryRepository = mockk<WorkSummaryRepository>()
            coEvery { summaryRepository.getSummary(any(), any(), any()) } returns
                DataResult.Success(summary(SummaryStatus.SUCCESS))
            val worker =
                checkWorker(
                    settings = AiSettings(autoWeeklySummaryEnabled = true),
                    duePeriods = listOf(period),
                    scheduler = scheduler,
                    stateRepository = stateRepository,
                    summaryRepository = summaryRepository,
                )

            assertSuccess(worker.doWork())
            coVerify(exactly = 1) { stateRepository.markEvaluated(SummaryType.WEEKLY, period.period.end) }
            coVerify(exactly = 0) { scheduler.enqueueGeneration(any(), any()) }
        }

    @Test
    fun `storage read failure asks WorkManager to retry`() =
        runBlocking {
            val summaryRepository = mockk<WorkSummaryRepository>()
            coEvery { summaryRepository.getSummary(any(), any(), any()) } returns DataResult.Failure(DataError.Storage)
            val worker =
                checkWorker(
                    settings = AiSettings(autoWeeklySummaryEnabled = true),
                    duePeriods = listOf(weeklyPeriod()),
                    summaryRepository = summaryRepository,
                )

            assertRetry(worker.doWork())
        }

    @Test
    fun `settings read failure asks WorkManager to retry`() =
        runBlocking {
            val worker =
                checkWorker(
                    settings = AiSettings(),
                    settingsRepository = ThrowingSettingsRepository(),
                )

            assertRetry(worker.doWork())
        }

    @Test
    fun `weekly and monthly due periods enqueue independently`() =
        runBlocking {
            val weekly = weeklyPeriod()
            val monthly =
                AutoSummaryPeriod(SummaryType.MONTHLY, DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
            val scheduler = mockk<AutoSummaryScheduler>()
            coEvery { scheduler.enqueueGeneration(any(), any()) } returns Result.success(Unit)
            val summaryRepository = mockk<WorkSummaryRepository>()
            coEvery { summaryRepository.getSummary(any(), any(), any()) } returns DataResult.Success(null)
            val worker =
                checkWorker(
                    settings = AiSettings(autoWeeklySummaryEnabled = true, autoMonthlySummaryEnabled = true),
                    duePeriods = listOf(weekly, monthly),
                    scheduler = scheduler,
                    summaryRepository = summaryRepository,
                )

            assertSuccess(worker.doWork())
            coVerify(exactly = 2) { scheduler.enqueueGeneration(any(), any()) }
        }

    @Test
    fun `a single check enqueues at most three oldest due periods`() =
        runBlocking {
            val periods =
                listOf(
                    weeklyPeriod(LocalDate.of(2026, 6, 1)),
                    weeklyPeriod(LocalDate.of(2026, 6, 8)),
                    weeklyPeriod(LocalDate.of(2026, 6, 15)),
                    weeklyPeriod(LocalDate.of(2026, 6, 22)),
                )
            val scheduler = mockk<AutoSummaryScheduler>()
            coEvery { scheduler.enqueueGeneration(any(), any()) } returns Result.success(Unit)
            val summaryRepository = mockk<WorkSummaryRepository>()
            coEvery { summaryRepository.getSummary(any(), any(), any()) } returns DataResult.Success(null)

            val worker =
                checkWorker(
                    settings = AiSettings(autoWeeklySummaryEnabled = true),
                    duePeriods = periods,
                    scheduler = scheduler,
                    summaryRepository = summaryRepository,
                )

            assertSuccess(worker.doWork())
            coVerify(exactly = 3) { scheduler.enqueueGeneration(any(), any()) }
            coVerify(exactly = 1) { scheduler.enqueueGeneration(periods.first(), any()) }
            coVerify(exactly = 0) { scheduler.enqueueGeneration(periods.last(), any()) }
        }

    @Suppress("LongParameterList")
    private fun checkWorker(
        settings: AiSettings,
        duePeriods: List<AutoSummaryPeriod> = emptyList(),
        scheduler: AutoSummaryScheduler = mockk(relaxed = true),
        stateRepository: AutoSummaryScheduleStateRepository = mockScheduleStateRepository(),
        summaryRepository: WorkSummaryRepository = mockk(relaxed = true),
        settingsRepository: AiSettingsRepository = FakeSettingsRepository(settings),
    ): AutoSummaryCheckWorker =
        AutoSummaryCheckWorker(
            context(),
            parameters(),
            AutoSummarySchedulingDependencies(
                AutoSummaryDuePeriodResolver { _, _, _ -> duePeriods },
                scheduler,
                settingsRepository,
                stateRepository,
            ),
            AutoSummaryCheckDependencies(
                summaryRepository,
                FixedLocalDateProvider(),
                TimeProvider { Instant.parse("2026-07-20T00:00:00Z") },
            ),
        )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GenerateSummaryWorkerTest {
    @Test
    fun `valid weekly success invokes automatic mode and notifies once`() =
        runBlocking {
            val useCase = mockk<GenerateWorkSummaryUseCase>()
            coEvery { useCase.invoke(any(), any(), SummaryGenerationMode.AUTOMATIC) } returns successfulResult()
            val notifications = mockk<AutoSummaryNotificationManager>(relaxed = true)
            val worker =
                generateWorker(
                    useCase,
                    notifications,
                    AiSettings(autoWeeklySummaryEnabled = true, notifyOnAutoSummaryCompletion = true),
                )

            assertSuccess(worker.doWork())
            coVerify(
                exactly = 1,
            ) { useCase.invoke(SummaryType.WEEKLY, weeklyPeriod().period, SummaryGenerationMode.AUTOMATIC) }
            coVerify(exactly = 1) { notifications.notifySuccess(SummaryType.WEEKLY, weeklyPeriod().period) }
        }

    @Test
    fun `success without notification preference does not post notification`() =
        runBlocking {
            val useCase = mockk<GenerateWorkSummaryUseCase>()
            coEvery { useCase.invoke(any(), any(), any()) } returns successfulResult()
            val notifications = mockk<AutoSummaryNotificationManager>(relaxed = true)
            val worker = generateWorker(useCase, notifications, AiSettings(autoWeeklySummaryEnabled = true))

            assertSuccess(worker.doWork())
            coVerify(exactly = 0) { notifications.notifySuccess(any(), any()) }
        }

    @Test
    fun `disabled type and malformed input safely skip without invoking generation`() =
        runBlocking {
            val useCase =
                mockk<GenerateWorkSummaryUseCase>(
                    relaxed = true,
                )
            val notifications = mockk<AutoSummaryNotificationManager>(relaxed = true)

            assertSuccess(generateWorker(useCase, notifications, AiSettings()).doWork())
            assertFailure(
                generateWorker(
                    useCase,
                    notifications,
                    AiSettings(autoWeeklySummaryEnabled = true),
                    Data.EMPTY,
                ).doWork(),
            )
            coVerify(exactly = 0) { useCase.invoke(any(), any(), any()) }
        }

    @Test
    fun `no eligible content and existing automatic skip are normal success without failure notification`() =
        runBlocking {
            val notifications = mockk<AutoSummaryNotificationManager>(relaxed = true)
            val noContent = mockk<GenerateWorkSummaryUseCase>()
            coEvery { noContent.invoke(any(), any(), any()) } returns GenerateWorkSummaryResult.NoEligibleContent(false)
            assertSuccess(
                generateWorker(
                    noContent,
                    notifications,
                    AiSettings(autoWeeklySummaryEnabled = true, notifyOnAutoSummaryCompletion = true),
                ).doWork(),
            )

            val skip = mockk<GenerateWorkSummaryUseCase>()
            coEvery { skip.invoke(any(), any(), any()) } returns
                GenerateWorkSummaryResult.Skipped(
                    com.worklogai.app.ai.usecase.AutomaticGenerationSkipReason.EXISTING_SUCCESS,
                )
            assertSuccess(
                generateWorker(
                    skip,
                    notifications,
                    AiSettings(autoWeeklySummaryEnabled = true, notifyOnAutoSummaryCompletion = true),
                ).doWork(),
            )
            coVerify(exactly = 0) { notifications.notifyFailure(any(), any()) }
        }

    @Test
    fun `retryable failure retries before final attempt and final failure notifies once`() =
        runBlocking {
            val useCase = mockk<GenerateWorkSummaryUseCase>()
            coEvery { useCase.invoke(any(), any(), any()) } returns
                GenerateWorkSummaryResult.Failure("超时", false, retryable = true)
            val notifications = mockk<AutoSummaryNotificationManager>(relaxed = true)

            assertRetry(
                generateWorker(
                    useCase,
                    notifications,
                    AiSettings(autoWeeklySummaryEnabled = true, notifyOnAutoSummaryCompletion = true),
                ).doWork(),
            )
            assertFailure(
                generateWorker(
                    useCase,
                    notifications,
                    AiSettings(autoWeeklySummaryEnabled = true, notifyOnAutoSummaryCompletion = true),
                    defaultInputData(),
                    runAttemptCount = 2,
                ).doWork(),
            )
            coVerify(exactly = 1) { notifications.notifyFailure(SummaryType.WEEKLY, weeklyPeriod().period) }
        }

    @Test
    fun `permanent failure does not retry`() =
        runBlocking {
            val useCase = mockk<GenerateWorkSummaryUseCase>()
            coEvery { useCase.invoke(any(), any(), any()) } returns GenerateWorkSummaryResult.Failure("配置缺失", false)

            assertFailure(
                generateWorker(useCase, mockk(relaxed = true), AiSettings(autoWeeklySummaryEnabled = true)).doWork(),
            )
        }

    @Test
    fun `settings read failure retries without invoking generation`() =
        runBlocking {
            val useCase = mockk<GenerateWorkSummaryUseCase>(relaxed = true)

            val worker =
                generateWorker(
                    useCase,
                    mockk(relaxed = true),
                    AiSettings(autoWeeklySummaryEnabled = true),
                    settingsRepository = ThrowingSettingsRepository(),
                )

            assertRetry(worker.doWork())
            coVerify(exactly = 0) { useCase.invoke(any(), any(), any()) }
        }

    @Suppress("LongParameterList")
    private fun generateWorker(
        useCase: GenerateWorkSummaryUseCase,
        notifications: AutoSummaryNotificationManager,
        settings: AiSettings,
        data: Data = defaultInputData(),
        runAttemptCount: Int = 0,
        settingsRepository: AiSettingsRepository = FakeSettingsRepository(settings),
    ): GenerateSummaryWorker =
        GenerateSummaryWorker(
            context(),
            parameters(data, runAttemptCount),
            GenerateSummaryDependencies(
                useCase,
                notifications,
                settingsRepository,
                FixedLocalDateProvider(),
                DefaultWorkPeriodCalculator(),
            ),
        )
}

private fun defaultInputData(): Data =
    Data
        .Builder()
        .putString(INPUT_TRIGGER, AUTO_TRIGGER)
        .putString(INPUT_SUMMARY_TYPE, SummaryType.WEEKLY.name)
        .putString(INPUT_PERIOD_START, "2026-07-13")
        .putString(INPUT_PERIOD_END, "2026-07-19")
        .build()

private fun weeklyPeriod(start: LocalDate = LocalDate.of(2026, 7, 13)) =
    AutoSummaryPeriod(SummaryType.WEEKLY, DateRange(start, start.plusDays(6)))

private fun successfulResult(): GenerateWorkSummaryResult.Success =
    GenerateWorkSummaryResult.Success(summary(SummaryStatus.SUCCESS), wasInputTruncated = false)

private fun summary(status: SummaryStatus): WorkSummary {
    val now = Instant.parse("2026-07-20T00:00:00Z")
    return WorkSummary(
        id = "summary",
        summaryType = SummaryType.WEEKLY,
        periodStart = LocalDate.of(2026, 7, 13),
        periodEnd = LocalDate.of(2026, 7, 19),
        status = status,
        sourceHash = "hash",
        aiProvider = "mock",
        modelName = "mock",
        originalContent = "{}",
        editedContent = "text",
        errorMessage = null,
        createdAt = now,
        updatedAt = now,
        generatedAt = now,
    )
}

private fun mockScheduleStateRepository(): AutoSummaryScheduleStateRepository =
    mockk {
        coEvery { getState() } returns Result.success(AutoSummaryScheduleState())
        coEvery { updateLastCheck(any()) } returns Result.success(Unit)
        coEvery { markEvaluated(any(), any()) } returns Result.success(Unit)
    }

private class FakeSettingsRepository(
    private val value: AiSettings,
) : AiSettingsRepository {
    override val settings: Flow<AiSettings> = flowOf(value)

    override suspend fun getSettings(): AiSettings = value

    override suspend fun saveSettings(settings: AiSettings): Result<Unit> = Result.success(Unit)
}

private class ThrowingSettingsRepository : AiSettingsRepository {
    override val settings: Flow<AiSettings> = flowOf(AiSettings())

    override suspend fun getSettings(): AiSettings = throw IOException("unavailable")

    override suspend fun saveSettings(settings: AiSettings): Result<Unit> = Result.failure(IOException("unavailable"))
}

private class FixedLocalDateProvider : LocalDateProvider {
    override fun today(): LocalDate = LocalDate.of(2026, 7, 20)

    override fun zoneId(): ZoneId = ZoneId.of("Asia/Shanghai")
}

private fun context(): Context = ApplicationProvider.getApplicationContext()

private fun parameters(
    data: Data = Data.EMPTY,
    runAttemptCount: Int = 0,
): WorkerParameters {
    val parameters = mockk<WorkerParameters>()
    every { parameters.inputData } returns data
    every { parameters.runAttemptCount } returns runAttemptCount
    return parameters
}

private fun assertSuccess(result: ListenableWorker.Result) {
    assertTrue(result is ListenableWorker.Result.Success)
}

private fun assertRetry(result: ListenableWorker.Result) {
    assertTrue(result is ListenableWorker.Result.Retry)
}

private fun assertFailure(result: ListenableWorker.Result) {
    assertTrue(result is ListenableWorker.Result.Failure)
}
