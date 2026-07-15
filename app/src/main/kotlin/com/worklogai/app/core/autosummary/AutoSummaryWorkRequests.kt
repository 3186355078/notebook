package com.worklogai.app.core.autosummary

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.worker.AutoSummaryCheckWorker
import com.worklogai.app.worker.GenerateSummaryWorker
import java.time.Duration
import javax.inject.Inject

interface AutoSummaryWorkGateway {
    fun enqueueUniquePeriodicCheck(request: PeriodicWorkRequest)

    fun enqueueUniqueImmediateCheck(request: OneTimeWorkRequest)

    fun enqueueUniqueGeneration(
        name: String,
        request: OneTimeWorkRequest,
    )

    fun cancelUniqueWork(name: String)

    fun cancelAllByTag(tag: String)
}

class WorkManagerAutoSummaryWorkGateway
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : AutoSummaryWorkGateway {
        override fun enqueueUniquePeriodicCheck(request: PeriodicWorkRequest) {
            workManager.enqueueUniquePeriodicWork(
                AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        override fun enqueueUniqueImmediateCheck(request: OneTimeWorkRequest) {
            workManager.enqueueUniqueWork(
                AUTO_SUMMARY_IMMEDIATE_CHECK_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        override fun enqueueUniqueGeneration(
            name: String,
            request: OneTimeWorkRequest,
        ) {
            workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        }

        override fun cancelUniqueWork(name: String) {
            workManager.cancelUniqueWork(name)
        }

        override fun cancelAllByTag(tag: String) {
            workManager.cancelAllWorkByTag(tag)
        }
    }

class AutoSummaryWorkRequestFactory
    @Inject
    constructor() {
        fun periodicCheck(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<AutoSummaryCheckWorker>(CHECK_INTERVAL, CHECK_FLEX)
                .setConstraints(checkConstraints())
                .addTag(AUTO_SUMMARY_TAG)
                .addTag(AUTO_SUMMARY_CHECK_TAG)
                .build()

        fun immediateCheck(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<AutoSummaryCheckWorker>()
                .setConstraints(checkConstraints())
                .addTag(AUTO_SUMMARY_TAG)
                .addTag(AUTO_SUMMARY_CHECK_TAG)
                .build()

        fun generate(
            period: AutoSummaryPeriod,
            settings: AiSettings,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<GenerateSummaryWorker>()
                .setInputData(
                    Data
                        .Builder()
                        .putString(INPUT_SUMMARY_TYPE, period.summaryType.name)
                        .putString(INPUT_PERIOD_START, period.period.start.toString())
                        .putString(INPUT_PERIOD_END, period.period.end.toString())
                        .putString(INPUT_TRIGGER, AUTO_TRIGGER)
                        .build(),
                ).setConstraints(generationConstraints(settings))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, GENERATION_BACKOFF_DELAY)
                .addTag(AUTO_SUMMARY_TAG)
                .addTag(AUTO_SUMMARY_GENERATE_TAG)
                .addTag(typeTag(period.summaryType))
                .build()

        fun generationWorkName(period: AutoSummaryPeriod): String =
            "auto-summary-generate-${period.summaryType.name.lowercase()}-" +
                "${period.period.start}-${period.period.end}-v$AUTO_SUMMARY_SCHEDULER_VERSION"

        private fun checkConstraints(): Constraints =
            Constraints
                .Builder()
                .setRequiresStorageNotLow(true)
                .build()

        private fun generationConstraints(settings: AiSettings): Constraints {
            val networkType =
                when {
                    settings.useMockProvider -> NetworkType.NOT_REQUIRED
                    settings.allowMobileNetwork -> NetworkType.CONNECTED
                    else -> NetworkType.UNMETERED
                }
            return Constraints
                .Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
        }
    }

fun typeTag(type: SummaryType): String = "auto-summary-generate-${type.name.lowercase()}"

const val AUTO_SUMMARY_TAG = "auto-summary"
const val AUTO_SUMMARY_CHECK_TAG = "auto-summary-check"
const val AUTO_SUMMARY_GENERATE_TAG = "auto-summary-generate"
const val AUTO_SUMMARY_PERIODIC_CHECK_WORK_NAME = "auto-summary-periodic-check-v1"
const val AUTO_SUMMARY_IMMEDIATE_CHECK_WORK_NAME = "auto-summary-immediate-check-v1"
const val INPUT_SUMMARY_TYPE = "summaryType"
const val INPUT_PERIOD_START = "periodStart"
const val INPUT_PERIOD_END = "periodEnd"
const val INPUT_TRIGGER = "trigger"
const val AUTO_TRIGGER = "AUTO"

private val CHECK_INTERVAL: Duration = Duration.ofHours(24)
private val CHECK_FLEX: Duration = Duration.ofHours(6)
private val GENERATION_BACKOFF_DELAY: Duration = Duration.ofMinutes(30)
