package com.worklogai.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.relation.WorkEntryWithContentEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
@Suppress("TooManyFunctions") // One explicit operation per required entry persistence action.
interface WorkEntryDao {
    @Query("SELECT * FROM work_entries ORDER BY entryDate ASC, id ASC")
    suspend fun getAllIncludingDeleted(): List<WorkEntryEntity>

    @Transaction
    @Query(
        "SELECT * FROM work_entries " +
            "WHERE entryDate = :entryDate AND isDeleted = 0 " +
            "LIMIT 1",
    )
    suspend fun getActiveWithContentByDate(entryDate: LocalDate): WorkEntryWithContentEntity?

    @Transaction
    @Query("SELECT * FROM work_entries WHERE entryDate = :entryDate LIMIT 1")
    suspend fun getWithContentByDateIncludingDeleted(entryDate: LocalDate): WorkEntryWithContentEntity?

    @Transaction
    @Query(
        "SELECT * FROM work_entries " +
            "WHERE entryDate = :entryDate AND isDeleted = 0 " +
            "LIMIT 1",
    )
    fun observeActiveWithContentByDate(entryDate: LocalDate): Flow<WorkEntryWithContentEntity?>

    @Transaction
    @Query(
        "SELECT * FROM work_entries " +
            "WHERE entryDate >= :startDate AND entryDate <= :endDate AND isDeleted = 0 " +
            "ORDER BY entryDate ASC",
    )
    suspend fun getActiveWithContentInDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<WorkEntryWithContentEntity>

    @Transaction
    @Query(
        "SELECT * FROM work_entries " +
            "WHERE isDeleted = 0 " +
            "ORDER BY entryDate DESC " +
            "LIMIT :limit OFFSET :offset",
    )
    suspend fun getRecentActiveWithContent(
        limit: Int,
        offset: Int,
    ): List<WorkEntryWithContentEntity>

    @Query("SELECT * FROM work_entries WHERE id = :entryId LIMIT 1")
    suspend fun getByIdIncludingDeleted(entryId: String): WorkEntryEntity?

    @Query("SELECT * FROM work_entries WHERE id = :entryId AND isDeleted = 0 LIMIT 1")
    suspend fun getActiveById(entryId: String): WorkEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WorkEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<WorkEntryEntity>)

    @Update
    suspend fun update(entry: WorkEntryEntity)

    @Query(
        "UPDATE work_entries " +
            "SET title = :title, updatedAt = :updatedAt " +
            "WHERE id = :entryId AND isDeleted = 0",
    )
    suspend fun updateTitle(
        entryId: String,
        title: String?,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE work_entries " +
            "SET allowAiProcessing = :allowAiProcessing, updatedAt = :updatedAt " +
            "WHERE id = :entryId AND isDeleted = 0",
    )
    suspend fun updateAllowAiProcessing(
        entryId: String,
        allowAiProcessing: Boolean,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE work_entries " +
            "SET isDeleted = 1, updatedAt = :updatedAt " +
            "WHERE id = :entryId AND isDeleted = 0",
    )
    suspend fun softDelete(
        entryId: String,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE work_entries " +
            "SET isDeleted = 0, updatedAt = :updatedAt " +
            "WHERE id = :entryId AND isDeleted = 1",
    )
    suspend fun restore(
        entryId: String,
        updatedAt: Instant,
    ): Int

    @Delete
    suspend fun delete(entry: WorkEntryEntity)

    @Query("DELETE FROM work_entries")
    suspend fun deleteAll(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM work_entries WHERE entryDate = :entryDate)")
    suspend fun existsByDateIncludingDeleted(entryDate: LocalDate): Boolean
}
