package com.worklogai.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface WorkSummaryDao {
    @Query(
        "SELECT * FROM work_summaries " +
            "WHERE summaryType = :summaryType " +
            "AND periodStart = :periodStart " +
            "AND periodEnd = :periodEnd " +
            "LIMIT 1",
    )
    suspend fun getByPeriod(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): WorkSummaryEntity?

    @Query(
        "SELECT * FROM work_summaries " +
            "WHERE summaryType = :summaryType " +
            "AND periodStart = :periodStart " +
            "AND periodEnd = :periodEnd " +
            "LIMIT 1",
    )
    fun observeByPeriod(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): Flow<WorkSummaryEntity?>

    @Query(
        "SELECT * FROM work_summaries " +
            "WHERE summaryType = :summaryType " +
            "ORDER BY periodStart DESC, periodEnd DESC",
    )
    fun observeByType(summaryType: SummaryType): Flow<List<WorkSummaryEntity>>

    @Query(
        "SELECT * FROM work_summaries " +
            "WHERE summaryType = :summaryType AND status != 'SUCCESS' " +
            "ORDER BY periodStart ASC, periodEnd ASC",
    )
    suspend fun getNotSuccessfulByType(summaryType: SummaryType): List<WorkSummaryEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insert(summary: WorkSummaryEntity)

    @Update
    suspend fun update(summary: WorkSummaryEntity)

    @Query(
        "UPDATE work_summaries " +
            "SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt " +
            "WHERE summaryType = :summaryType " +
            "AND periodStart = :periodStart " +
            "AND periodEnd = :periodEnd",
    )
    @Suppress("LongParameterList") // SQL binds a composite period and its update payload atomically.
    suspend fun updateStatus(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        status: SummaryStatus,
        errorMessage: String?,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE work_summaries SET " +
            "status = 'SUCCESS', sourceHash = :sourceHash, aiProvider = :aiProvider, " +
            "modelName = :modelName, originalContent = :originalContent, editedContent = :editedContent, " +
            "errorMessage = NULL, " +
            "generatedAt = :generatedAt, updatedAt = :updatedAt " +
            "WHERE summaryType = :summaryType " +
            "AND periodStart = :periodStart " +
            "AND periodEnd = :periodEnd",
    )
    @Suppress("LongParameterList") // SQL binds a composite period and generation payload atomically.
    suspend fun updateGeneratedContent(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        sourceHash: String,
        aiProvider: String,
        modelName: String,
        originalContent: String,
        editedContent: String,
        generatedAt: Instant,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE work_summaries " +
            "SET editedContent = :editedContent, updatedAt = :updatedAt " +
            "WHERE summaryType = :summaryType " +
            "AND periodStart = :periodStart " +
            "AND periodEnd = :periodEnd",
    )
    suspend fun updateEditedContent(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        editedContent: String?,
        updatedAt: Instant,
    ): Int

    @Delete
    suspend fun delete(summary: WorkSummaryEntity)
}
