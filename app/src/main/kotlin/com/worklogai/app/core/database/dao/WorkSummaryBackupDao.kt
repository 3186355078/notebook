package com.worklogai.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.worklogai.app.core.database.entity.WorkSummaryEntity

/** Snapshot-only DAO kept separate from the summary workflow DAO. */
@Dao
interface WorkSummaryBackupDao {
    @Query("SELECT * FROM work_summaries ORDER BY summaryType ASC, periodStart ASC, periodEnd ASC, id ASC")
    suspend fun getAll(): List<WorkSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(summaries: List<WorkSummaryEntity>)

    @Query("DELETE FROM work_summaries")
    suspend fun deleteAll(): Int
}
