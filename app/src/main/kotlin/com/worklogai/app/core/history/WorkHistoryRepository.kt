package com.worklogai.app.core.history

import com.worklogai.app.core.common.result.DataResult
import java.time.LocalDate

/**
 * Read boundary for the lightweight history experience. Full editor aggregates remain owned by
 * [com.worklogai.app.core.repository.WorkEntryRepository].
 */
interface WorkHistoryRepository {
    suspend fun getRecentEntries(
        limit: Int,
        offset: Int,
    ): DataResult<WorkHistoryPage>

    suspend fun getEntriesInRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): DataResult<List<WorkEntrySummary>>

    suspend fun searchEntries(
        query: String,
        limit: Int,
        offset: Int,
    ): DataResult<WorkHistoryPage>
}
