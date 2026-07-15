package com.worklogai.app.app

import android.app.Application
import androidx.work.Configuration
import com.worklogai.app.core.autosummary.AutoSummaryStartupCoordinator
import com.worklogai.app.core.backup.RestoreStartupRecoveryCoordinator
import com.worklogai.app.worker.WorkLogWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WorkLogApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: WorkLogWorkerFactory

    @Inject
    lateinit var autoSummaryStartupCoordinator: AutoSummaryStartupCoordinator

    @Inject
    internal lateinit var restoreStartupRecoveryCoordinator: RestoreStartupRecoveryCoordinator

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        restoreStartupRecoveryCoordinator.reconcileInBackground()
        autoSummaryStartupCoordinator.reconcileInBackground()
    }
}
