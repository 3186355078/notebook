package com.worklogai.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
@Suppress("TooManyFunctions")
interface TodoDao {
    @Query("${TODO_SELECT_BY_DATE} ${TODO_ORDER_BY}")
    fun observeByDate(date: LocalDate): Flow<List<TodoEntity>>

    @Query("${TODO_SELECT_BY_DATE} ${TODO_ORDER_BY}")
    suspend fun getByDate(date: LocalDate): List<TodoEntity>

    @Query(
        "SELECT * FROM todo_items " +
            "WHERE scheduledDate < :date AND status IN ('NOT_STARTED', 'IN_PROGRESS') " +
            TODO_ORDER_BY,
    )
    fun observeIncompleteBefore(date: LocalDate): Flow<List<TodoEntity>>

    @Query(
        "SELECT * FROM todo_items " +
            "WHERE scheduledDate < :date AND status IN ('NOT_STARTED', 'IN_PROGRESS') " +
            TODO_ORDER_BY,
    )
    suspend fun getIncompleteBefore(date: LocalDate): List<TodoEntity>

    @Query("SELECT * FROM todo_items ORDER BY scheduledDate ASC, createdAt ASC, id ASC")
    suspend fun getAll(): List<TodoEntity>

    @Query("SELECT * FROM todo_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TodoEntity?

    @Query("SELECT COUNT(*) FROM todo_items WHERE scheduledDate = :date AND title = :title")
    suspend fun countByDateAndTitle(
        date: LocalDate,
        title: String,
    ): Int

    @Query(
        "SELECT scheduledDate, COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) AS done " +
            "FROM todo_items WHERE scheduledDate IN (:dates) GROUP BY scheduledDate",
    )
    suspend fun getStatsByDates(dates: Set<LocalDate>): List<TodoDateStatsRow>

    @Query(
        "SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM todo_items " +
            "WHERE scheduledDate = :date AND priority = :priority " +
            "AND status IN ('NOT_STARTED', 'IN_PROGRESS')",
    )
    suspend fun nextSortOrder(
        date: LocalDate,
        priority: TodoPriority,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(todo: TodoEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(todos: List<TodoEntity>)

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("DELETE FROM todo_items")
    suspend fun deleteAll(): Int

    @Query(
        "UPDATE todo_items SET status = :status, completionNote = :completionNote, " +
            "completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateStatus(
        id: String,
        status: TodoStatus,
        completionNote: String?,
        completedAt: Instant?,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE todo_items SET scheduledDate = :date, priority = :priority, " +
            "sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updatePlacement(
        id: String,
        date: LocalDate,
        priority: TodoPriority,
        sortOrder: Int,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE todo_items SET linkedContentBlockId = :blockId, updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateLinkedBlock(
        id: String,
        blockId: String?,
        updatedAt: Instant,
    ): Int

    @Query(
        "UPDATE todo_items SET linkedContentBlockId = NULL, updatedAt = :updatedAt " +
            "WHERE linkedContentBlockId = :blockId",
    )
    suspend fun clearLinkedBlock(
        blockId: String,
        updatedAt: Instant,
    ): Int

    @Transaction
    suspend fun updateSortOrders(
        date: LocalDate,
        placements: List<TodoOrderUpdate>,
        updatedAt: Instant,
    ) {
        placements.forEach { placement ->
            check(
                updatePlacement(
                    id = placement.id,
                    date = date,
                    priority = placement.priority,
                    sortOrder = placement.sortOrder,
                    updatedAt = updatedAt,
                ) == 1,
            )
        }
    }

    companion object {
        private const val TODO_SELECT_BY_DATE = "SELECT * FROM todo_items WHERE scheduledDate = :date"
        private const val TODO_ORDER_BY =
            "ORDER BY CASE WHEN status IN ('NOT_STARTED', 'IN_PROGRESS') THEN 0 ELSE 1 END, " +
                "CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 " +
                "WHEN 'MEDIUM' THEN 2 ELSE 3 END, sortOrder ASC, createdAt ASC, id ASC"
    }
}

data class TodoOrderUpdate(
    val id: String,
    val priority: TodoPriority,
    val sortOrder: Int,
)

data class TodoDateStatsRow(
    val scheduledDate: LocalDate,
    val total: Int,
    val done: Int,
)
