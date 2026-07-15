package com.worklogai.app.core.autosummary

import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.datastore.AiSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoSummaryStartupCoordinator
    @Inject
    constructor(
        private val settingsRepository: AiSettingsRepository,
        private val scheduler: AutoSummaryScheduler,
        private val scheduleStateRepository: AutoSummaryScheduleStateRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        fun reconcileInBackground() {
            scope.launch { reconcile() }
        }

        suspend fun reconcile() {
            try {
                val settings = settingsRepository.getSettings()
                val state = scheduleStateRepository.getState().getOrNull() ?: return
                val isVersionChanged = state.schedulerVersion != AUTO_SUMMARY_SCHEDULER_VERSION
                val scheduled = scheduler.applySettings(settings, enqueueImmediateCheck = isVersionChanged)
                if (isVersionChanged && scheduled.isSuccess) {
                    scheduleStateRepository.updateSchedulerVersion(AUTO_SUMMARY_SCHEDULER_VERSION)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                // A later application start retries this lightweight reconciliation.
            } catch (_: IllegalStateException) {
                // Scheduling is retried by the next start or settings update.
            }
        }
    }
