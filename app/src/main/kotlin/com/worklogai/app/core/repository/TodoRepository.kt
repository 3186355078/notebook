package com.worklogai.app.core.repository

import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class CreateTodoRequest(
    val scheduledDate: LocalDate,
    val title: String,
    val note: String? = null,
    val priority: TodoPriority = TodoPriority.MEDIUM,
)

data class UpdateTodoRequest(
    val id: String,
    val scheduledDate: LocalDate,
    val title: String,
    val note: String?,
    val priority: TodoPriority,
    val status: TodoStatus,
    val completionNote: String?,
)

data class TodoPlacement(
    val id: String,
    val priority: TodoPriority,
)

data class TodoDateStats(
    val total: Int,
    val done: Int,
)

@Suppress("TooManyFunctions")
interface TodoRepository {
    fun observeByDate(date: LocalDate): Flow<DataResult<List<DailyTodo>>>

    fun observeIncompleteBefore(date: LocalDate): Flow<DataResult<List<DailyTodo>>>

    suspend fun getByDate(date: LocalDate): DataResult<List<DailyTodo>>

    suspend fun getById(id: String): DataResult<DailyTodo>

    suspend fun create(request: CreateTodoRequest): DataResult<DailyTodo>

    suspend fun update(request: UpdateTodoRequest): DataResult<DailyTodo>

    suspend fun delete(id: String): DataResult<Unit>

    suspend fun updateStatus(
        id: String,
        status: TodoStatus,
        completionNote: String? = null,
    ): DataResult<DailyTodo>

    suspend fun reorder(
        date: LocalDate,
        placements: List<TodoPlacement>,
    ): DataResult<Unit>

    suspend fun moveIncompleteToDate(
        ids: Set<String>,
        targetDate: LocalDate,
    ): DataResult<List<DailyTodo>>

    suspend fun bindContentBlock(
        id: String,
        blockId: String?,
    ): DataResult<DailyTodo>

    suspend fun getStatsByDates(dates: Set<LocalDate>): DataResult<Map<LocalDate, TodoDateStats>> =
        DataResult.Success(emptyMap())
}
