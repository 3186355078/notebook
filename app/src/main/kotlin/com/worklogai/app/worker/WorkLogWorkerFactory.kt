package com.worklogai.app.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WorkLogWorkerFactory
    @Inject
    constructor(
        private val schedulingDependencies: Provider<AutoSummarySchedulingDependencies>,
        private val checkDependencies: AutoSummaryCheckDependencies,
        private val generateDependencies: GenerateSummaryDependencies,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            when (workerClassName) {
                AutoSummaryCheckWorker::class.java.name ->
                    AutoSummaryCheckWorker(
                        appContext,
                        workerParameters,
                        schedulingDependencies.get(),
                        checkDependencies,
                    )
                GenerateSummaryWorker::class.java.name ->
                    GenerateSummaryWorker(
                        appContext,
                        workerParameters,
                        generateDependencies,
                    )
                else -> null
            }
    }
