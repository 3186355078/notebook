package com.worklogai.app.core.repository

import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.model.GeneratedSummaryContent
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WorkSummaryRepository {
    fun observeSummary(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): Flow<DataResult<WorkSummary?>>

    fun observeSummaries(summaryType: SummaryType): Flow<DataResult<List<WorkSummary>>>

    suspend fun getSummary(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): DataResult<WorkSummary?>

    suspend fun saveSummary(summary: WorkSummary): DataResult<WorkSummary>

    suspend fun updateStatus(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        status: SummaryStatus,
        errorMessage: String? = null,
    ): DataResult<Unit>

    suspend fun saveGeneratedContent(content: GeneratedSummaryContent): DataResult<Unit>

    suspend fun updateEditedContent(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        editedContent: String?,
    ): DataResult<Unit>

    suspend fun getNotSuccessfulSummaries(summaryType: SummaryType): DataResult<List<WorkSummary>>

    suspend fun deleteSummary(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): DataResult<Unit>
}
