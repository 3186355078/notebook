package com.worklogai.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.worklogai.app.ai.usecase.GenerateWorkSummaryResult
import com.worklogai.app.ai.usecase.SummaryGenerationMode
import com.worklogai.app.core.autosummary.AUTO_TRIGGER
import com.worklogai.app.core.autosummary.INPUT_PERIOD_END
import com.worklogai.app.core.autosummary.INPUT_PERIOD_START
import com.worklogai.app.core.autosummary.INPUT_SUMMARY_TYPE
import com.worklogai.app.core.autosummary.INPUT_TRIGGER
import com.worklogai.app.core.autosummary.MAX_AUTO_SUMMARY_RUN_ATTEMPTS
import com.worklogai.app.core.autosummary.toAutoSummarySettings
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

class GenerateSummaryWorker
    constructor(
        appContext: Context,
        workerParameters: WorkerParameters,
        private val dependencies: GenerateSummaryDependencies,
    ) : CoroutineWorker(appContext, workerParameters) {
        override suspend fun doWork(): Result =
            try {
                generate()
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                Result.retry()
            }

        private suspend fun generate(): Result =
            AutoSummaryWorkerInput
                .from(inputData)
                ?.takeIf { input -> isCompletedCanonicalPeriod(input.summaryType, input.period) }
                ?.let { input ->
                    val settings = dependencies.settingsRepository.getSettings()
                    if (settings.toAutoSummarySettings().isEnabled(input.summaryType)) {
                        performAutomaticGeneration(input, settings)
                    } else {
                        Result.success()
                    }
                } ?: Result.failure()

        private suspend fun performAutomaticGeneration(
            input: AutoSummaryWorkerInput,
            settings: com.worklogai.app.core.datastore.AiSettings,
        ): Result =
            when (
                val result =
                    dependencies.generateWorkSummary(
                        input.summaryType,
                        input.period,
                        SummaryGenerationMode.AUTOMATIC,
                    )
            ) {
                is GenerateWorkSummaryResult.Success -> {
                    if (settings.notifyOnAutoSummaryCompletion) {
                        dependencies.notificationManager.notifySuccess(input.summaryType, input.period)
                    }
                    Result.success()
                }
                is GenerateWorkSummaryResult.NoEligibleContent,
                is GenerateWorkSummaryResult.Skipped,
                -> Result.success()
                is GenerateWorkSummaryResult.Failure -> failureResult(result, settings, input)
            }

        private fun isCompletedCanonicalPeriod(
            type: SummaryType,
            period: DateRange,
        ): Boolean {
            if (period.end >= dependencies.localDateProvider.today()) return false
            return when (type) {
                SummaryType.WEEKLY ->
                    dependencies.workPeriodCalculator.weekContaining(period.start) == period
                SummaryType.MONTHLY ->
                    dependencies.workPeriodCalculator.monthContaining(YearMonth.from(period.start)) == period
            }
        }

        private fun failureResult(
            result: GenerateWorkSummaryResult.Failure,
            settings: com.worklogai.app.core.datastore.AiSettings,
            input: AutoSummaryWorkerInput,
        ): Result {
            val canRetry = result.retryable && runAttemptCount < MAX_AUTO_SUMMARY_RUN_ATTEMPTS - 1
            if (canRetry) return Result.retry()
            if (settings.notifyOnAutoSummaryCompletion) {
                dependencies.notificationManager.notifyFailure(input.summaryType, input.period)
            }
            return Result.failure()
        }
    }

private data class AutoSummaryWorkerInput(
    val summaryType: SummaryType,
    val period: DateRange,
) {
    companion object {
        fun from(data: androidx.work.Data): AutoSummaryWorkerInput? {
            val type = data.getString(INPUT_SUMMARY_TYPE)?.let(::parseSummaryType)
            val start = data.getString(INPUT_PERIOD_START)?.let(::parseDate)
            val end = data.getString(INPUT_PERIOD_END)?.let(::parseDate)
            return runCatching {
                require(data.getString(INPUT_TRIGGER) == AUTO_TRIGGER)
                AutoSummaryWorkerInput(
                    summaryType = requireNotNull(type),
                    period = DateRange(requireNotNull(start), requireNotNull(end)),
                )
            }.getOrNull()
        }

        private fun parseSummaryType(value: String): SummaryType? =
            runCatching { SummaryType.valueOf(value) }.getOrNull()

        private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
    }
}
