package com.worklogai.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.worklogai.app.core.autosummary.AutoSummaryPeriod
import com.worklogai.app.core.autosummary.MAX_AUTO_SUMMARY_PERIODS_PER_CHECK
import com.worklogai.app.core.autosummary.toAutoSummarySettings
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.model.SummaryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class AutoSummaryCheckWorker
    constructor(
        appContext: Context,
        workerParameters: WorkerParameters,
        private val schedulingDependencies: AutoSummarySchedulingDependencies,
        private val checkDependencies: AutoSummaryCheckDependencies,
    ) : CoroutineWorker(appContext, workerParameters) {
        override suspend fun doWork(): Result =
            try {
                autoSummaryCheckMutex.withLock { runCheck() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                Result.retry()
            }

        private suspend fun runCheck(): Result {
            val settings = schedulingDependencies.settingsRepository.getSettings()
            val autoSettings = settings.toAutoSummarySettings()
            return if (autoSettings.isEnabled) {
                runEnabledCheck(settings, autoSettings)
            } else {
                Result.success()
            }
        }

        private suspend fun runEnabledCheck(
            settings: com.worklogai.app.core.datastore.AiSettings,
            autoSettings: com.worklogai.app.core.autosummary.AutoSummarySettings,
        ): Result {
            val state = schedulingDependencies.scheduleStateRepository.getState().getOrElse { return Result.retry() }
            val duePeriods =
                schedulingDependencies.duePeriodResolver
                    .resolve(checkDependencies.localDateProvider.today(), autoSettings, state)
                    .take(MAX_AUTO_SUMMARY_PERIODS_PER_CHECK)
            var shouldRetry = false
            for (period in duePeriods) {
                when (evaluate(period, settings)) {
                    EvaluationDecision.Enqueue -> {
                        shouldRetry = schedulingDependencies.scheduler.enqueueGeneration(period, settings).isFailure
                    }
                    EvaluationDecision.MarkEvaluated -> {
                        shouldRetry =
                            schedulingDependencies.scheduleStateRepository
                                .markEvaluated(period.summaryType, period.period.end)
                                .isFailure
                    }
                    EvaluationDecision.Retry -> shouldRetry = true
                }
                if (shouldRetry) break
            }
            val lastCheckUpdated =
                !shouldRetry &&
                    schedulingDependencies.scheduleStateRepository
                        .updateLastCheck(checkDependencies.timeProvider.now())
                        .isSuccess
            return if (lastCheckUpdated) {
                Result.success()
            } else {
                Result.retry()
            }
        }

        private suspend fun evaluate(
            period: AutoSummaryPeriod,
            settings: com.worklogai.app.core.datastore.AiSettings,
        ): EvaluationDecision {
            if (!settings.toAutoSummarySettings().isEnabled(period.summaryType)) {
                return EvaluationDecision.MarkEvaluated
            }
            return when (
                val result =
                    checkDependencies.workSummaryRepository.getSummary(
                        period.summaryType,
                        period.period.start,
                        period.period.end,
                    )
            ) {
                is DataResult.Failure -> EvaluationDecision.Retry
                is DataResult.Success ->
                    when (result.value?.status) {
                        SummaryStatus.SUCCESS,
                        SummaryStatus.GENERATING,
                        SummaryStatus.FAILED,
                        -> EvaluationDecision.MarkEvaluated
                        SummaryStatus.PENDING,
                        null,
                        -> EvaluationDecision.Enqueue
                    }
            }
        }
    }

private enum class EvaluationDecision {
    Enqueue,
    MarkEvaluated,
    Retry,
}

private val autoSummaryCheckMutex = Mutex()
