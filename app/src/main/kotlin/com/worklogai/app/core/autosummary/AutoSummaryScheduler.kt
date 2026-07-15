package com.worklogai.app.core.autosummary

import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

interface AutoSummaryScheduler {
    suspend fun applySettings(
        settings: AiSettings,
        enqueueImmediateCheck: Boolean,
    ): Result<Unit>

    suspend fun enqueueImmediateCheck(): Result<Unit>

    suspend fun enqueueGeneration(
        period: AutoSummaryPeriod,
        settings: AiSettings,
    ): Result<Unit>

    suspend fun cancelAutomaticGeneration(type: SummaryType? = null): Result<Unit>
}

class DefaultAutoSummaryScheduler
    @Inject
    constructor(
        private val workGateway: AutoSummaryWorkGateway,
        private val requestFactory: AutoSummaryWorkRequestFactory,
    ) : AutoSummaryScheduler {
        override suspend fun applySettings(
            settings: AiSettings,
            enqueueImmediateCheck: Boolean,
        ): Result<Unit> =
            guarded {
                if (settings.toAutoSummarySettings().isEnabled) {
                    workGateway.enqueueUniquePeriodicCheck(requestFactory.periodicCheck())
                    if (enqueueImmediateCheck) {
                        workGateway.enqueueUniqueImmediateCheck(requestFactory.immediateCheck())
                    }
                } else {
                    workGateway.cancelUniqueWork(AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME)
                    workGateway.cancelUniqueWork(AUTO_SUMMARY_IMMEDIATE_CHECK_WORK_NAME)
                    workGateway.cancelAllByTag(AUTO_SUMMARY_GENERATE_TAG)
                }
            }

        override suspend fun enqueueImmediateCheck(): Result<Unit> =
            guarded { workGateway.enqueueUniqueImmediateCheck(requestFactory.immediateCheck()) }

        override suspend fun enqueueGeneration(
            period: AutoSummaryPeriod,
            settings: AiSettings,
        ): Result<Unit> =
            guarded {
                workGateway.enqueueUniqueGeneration(
                    requestFactory.generationWorkName(period),
                    requestFactory.generate(period, settings),
                )
            }

        override suspend fun cancelAutomaticGeneration(type: SummaryType?): Result<Unit> =
            guarded { workGateway.cancelAllByTag(type?.let(::typeTag) ?: AUTO_SUMMARY_GENERATE_TAG) }

        private suspend fun guarded(block: () -> Unit): Result<Unit> =
            try {
                block()
                Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalStateException) {
                Result.failure(AutoSummarySchedulingException())
            }
    }

class AutoSummarySchedulingException : IllegalStateException("Unable to schedule automatic summaries.")
