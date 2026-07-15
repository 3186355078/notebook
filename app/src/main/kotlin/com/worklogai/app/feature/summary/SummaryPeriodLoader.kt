package com.worklogai.app.feature.summary

import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkillRequest
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.database.hash.SummarySourceHasher
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.repository.WorkSummaryRepository
import javax.inject.Inject

internal sealed interface SummaryPeriodLoadResult {
    data class Success(
        val entryCount: Int,
        val eligibleEntryCount: Int,
        val summary: WorkSummary?,
        val currentSourceHash: String?,
    ) : SummaryPeriodLoadResult

    data object Failure : SummaryPeriodLoadResult
}

class SummaryPeriodLoader
    @Inject
    constructor(
        private val workEntryRepository: WorkEntryRepository,
        private val workSummaryRepository: WorkSummaryRepository,
        private val summarySourceHasher: SummarySourceHasher,
        private val workSummarySkill: WorkSummarySkill,
    ) {
        internal suspend fun load(
            summaryType: SummaryType,
            period: DateRange,
        ): SummaryPeriodLoadResult =
            when (val entryResult = workEntryRepository.getEntries(period.start, period.end)) {
                is DataResult.Failure -> SummaryPeriodLoadResult.Failure
                is DataResult.Success -> loadSummary(summaryType, period, entryResult.value)
            }

        private suspend fun loadSummary(
            summaryType: SummaryType,
            period: DateRange,
            entries: List<WorkEntry>,
        ): SummaryPeriodLoadResult =
            when (val summaryResult = workSummaryRepository.getSummary(summaryType, period.start, period.end)) {
                is DataResult.Failure -> SummaryPeriodLoadResult.Failure
                is DataResult.Success -> buildLoadedResult(summaryType, period, entries, summaryResult.value)
            }

        private fun buildLoadedResult(
            summaryType: SummaryType,
            period: DateRange,
            entries: List<WorkEntry>,
            summary: WorkSummary?,
        ): SummaryPeriodLoadResult {
            val prepared =
                workSummarySkill
                    .prepare(
                        entries,
                        WorkSummarySkillRequest(
                            summaryType,
                            period.start,
                            period.end,
                            "zh-CN",
                        ),
                    ).getOrNull()
            return SummaryPeriodLoadResult.Success(
                entryCount = entries.size,
                eligibleEntryCount = prepared?.input?.entries?.size ?: 0,
                summary = summary,
                currentSourceHash = prepared?.eligibleEntries?.let(summarySourceHasher::hash),
            )
        }
    }
