package com.worklogai.app.feature.summary

import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.ai.usecase.GenerateWorkSummaryUseCase
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.history.WorkPeriodCalculator
import com.worklogai.app.core.repository.WorkSummaryRepository
import javax.inject.Inject

class SummaryViewModelDependencies
    @Inject
    constructor(
        val generateWorkSummary: GenerateWorkSummaryUseCase,
        val summaryPeriodLoader: SummaryPeriodLoader,
        val workSummaryRepository: WorkSummaryRepository,
        val workPeriodCalculator: WorkPeriodCalculator,
        val workSummarySkill: WorkSummarySkill,
        val timeProvider: TimeProvider,
    )
