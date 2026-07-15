package com.worklogai.app.worker

import com.worklogai.app.ai.usecase.GenerateWorkSummaryUseCase
import com.worklogai.app.core.autosummary.AutoSummaryDuePeriodResolver
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.history.WorkPeriodCalculator
import com.worklogai.app.core.repository.WorkSummaryRepository
import javax.inject.Inject

class AutoSummarySchedulingDependencies
    @Inject
    constructor(
        val duePeriodResolver: AutoSummaryDuePeriodResolver,
        val scheduler: AutoSummaryScheduler,
        val settingsRepository: AiSettingsRepository,
        val scheduleStateRepository: AutoSummaryScheduleStateRepository,
    )

class AutoSummaryCheckDependencies
    @Inject
    constructor(
        val workSummaryRepository: WorkSummaryRepository,
        val localDateProvider: LocalDateProvider,
        val timeProvider: TimeProvider,
    )

class GenerateSummaryDependencies
    @Inject
    constructor(
        val generateWorkSummary: GenerateWorkSummaryUseCase,
        val notificationManager: AutoSummaryNotificationManager,
        val settingsRepository: AiSettingsRepository,
        val localDateProvider: LocalDateProvider,
        val workPeriodCalculator: WorkPeriodCalculator,
    )
