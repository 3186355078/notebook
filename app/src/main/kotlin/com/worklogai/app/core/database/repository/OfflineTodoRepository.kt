package com.worklogai.app.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.dao.TodoDao
import com.worklogai.app.core.database.dao.TodoOrderUpdate
import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.database.mapper.toDomain
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TODO_COMPLETION_NOTE_MAX_LENGTH
import com.worklogai.app.core.model.TODO_NOTE_MAX_LENGTH
import com.worklogai.app.core.model.TODO_TITLE_MAX_LENGTH
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import com.worklogai.app.core.model.isIncomplete
import com.worklogai.app.core.repository.CreateTodoRequest
import com.worklogai.app.core.repository.TodoDateStats
import com.worklogai.app.core.repository.TodoPlacement
import com.worklogai.app.core.repository.TodoRepository
import com.worklogai.app.core.repository.UpdateTodoRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@Suppress("TooManyFunctions")
class OfflineTodoRepository
    @Inject
    constructor(
        private val database: WorkLogDatabase,
        private val todoDao: TodoDao,
        private val idGenerator: IdGenerator,
        private val timeProvider: TimeProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : TodoRepository {
        override fun observeByDate(date: LocalDate): Flow<DataResult<List<DailyTodo>>> =
            todoDao
                .observeByDate(date)
                .map { entities -> DataResult.Success(entities.map(TodoEntity::toDomain)) }
                .catchStorageErrors()
                .flowOn(ioDispatcher)

        override fun observeIncompleteBefore(date: LocalDate): Flow<DataResult<List<DailyTodo>>> =
            todoDao
                .observeIncompleteBefore(date)
                .map { entities -> DataResult.Success(entities.map(TodoEntity::toDomain)) }
                .catchStorageErrors()
                .flowOn(ioDispatcher)

        override suspend fun getByDate(date: LocalDate): DataResult<List<DailyTodo>> =
            databaseResult { DataResult.Success(todoDao.getByDate(date).map(TodoEntity::toDomain)) }

        override suspend fun getById(id: String): DataResult<DailyTodo> =
            databaseResult { todoDao.findById(id)?.toDomain()?.success() ?: notFound() }

        override suspend fun create(request: CreateTodoRequest): DataResult<DailyTodo> {
            val title = request.title.normalizedTitle()
            if (title == null || !request.note.isValidOptionalText(TODO_NOTE_MAX_LENGTH)) return invalidTodo()
            val note = request.note.normalized(TODO_NOTE_MAX_LENGTH)
            return databaseResult {
                database.withTransaction {
                    val now = timeProvider.now()
                    val entity =
                        TodoEntity(
                            id = idGenerator.generate(),
                            scheduledDate = request.scheduledDate,
                            title = title,
                            note = note,
                            priority = request.priority,
                            status = TodoStatus.NOT_STARTED,
                            sortOrder = todoDao.nextSortOrder(request.scheduledDate, request.priority),
                            completionNote = null,
                            linkedContentBlockId = null,
                            createdAt = now,
                            updatedAt = now,
                            completedAt = null,
                        )
                    todoDao.insert(entity)
                    entity.toDomain().success()
                }
            }
        }

        override suspend fun update(request: UpdateTodoRequest): DataResult<DailyTodo> {
            val title = request.title.normalizedTitle()
            if (
                title == null ||
                !request.note.isValidOptionalText(TODO_NOTE_MAX_LENGTH) ||
                !request.completionNote.isValidOptionalText(TODO_COMPLETION_NOTE_MAX_LENGTH)
            ) {
                return invalidTodo()
            }
            return databaseResult {
                database.withTransaction {
                    val existing = todoDao.findById(request.id) ?: return@withTransaction notFound()
                    val now = timeProvider.now()
                    val placementChanged =
                        existing.scheduledDate != request.scheduledDate ||
                            existing.priority != request.priority
                    val updated =
                        existing.copy(
                            scheduledDate = request.scheduledDate,
                            title = title,
                            note = request.note.normalized(TODO_NOTE_MAX_LENGTH),
                            priority = request.priority,
                            status = request.status,
                            completionNote = request.completionNote.normalized(TODO_COMPLETION_NOTE_MAX_LENGTH),
                            sortOrder =
                                if (placementChanged && request.status.isIncomplete) {
                                    todoDao.nextSortOrder(request.scheduledDate, request.priority)
                                } else {
                                    existing.sortOrder
                                },
                            completedAt =
                                when {
                                    request.status == TodoStatus.DONE -> existing.completedAt ?: now
                                    else -> null
                                },
                            updatedAt = now,
                        )
                    todoDao.update(updated)
                    updated.toDomain().success()
                }
            }
        }

        override suspend fun delete(id: String): DataResult<Unit> =
            databaseResult {
                val existing = todoDao.findById(id) ?: return@databaseResult notFound()
                todoDao.delete(existing)
                Unit.success()
            }

        override suspend fun updateStatus(
            id: String,
            status: TodoStatus,
            completionNote: String?,
        ): DataResult<DailyTodo> {
            if (!completionNote.isValidOptionalText(TODO_COMPLETION_NOTE_MAX_LENGTH)) return invalidTodo()
            return databaseResult {
                database.withTransaction {
                    val existing = todoDao.findById(id) ?: return@withTransaction notFound()
                    val now = timeProvider.now()
                    val normalizedNote =
                        if (completionNote == null) {
                            existing.completionNote
                        } else {
                            completionNote.normalized(TODO_COMPLETION_NOTE_MAX_LENGTH)
                        }
                    check(
                        todoDao.updateStatus(
                            id = id,
                            status = status,
                            completionNote = normalizedNote,
                            completedAt = if (status == TodoStatus.DONE) existing.completedAt ?: now else null,
                            updatedAt = now,
                        ) == 1,
                    )
                    todoDao.findById(id)?.toDomain()?.success() ?: notFound()
                }
            }
        }

        override suspend fun reorder(
            date: LocalDate,
            placements: List<TodoPlacement>,
        ): DataResult<Unit> =
            databaseResult {
                database.withTransaction {
                    val incomplete = todoDao.getByDate(date).filter { it.status.isIncomplete }
                    val ids = placements.map(TodoPlacement::id)
                    val valid =
                        ids.size == incomplete.size &&
                            ids.distinct().size == ids.size &&
                            ids.toSet() == incomplete.map(TodoEntity::id).toSet()
                    if (!valid) {
                        return@withTransaction DataResult.Failure(
                            DataError.Validation(DataValidationReason.INVALID_ORDERING),
                        )
                    }
                    val counters = TodoPriority.entries.associateWith { 0 }.toMutableMap()
                    val normalized =
                        placements.map { placement ->
                            val order = counters.getValue(placement.priority)
                            counters[placement.priority] = order + 1
                            TodoOrderUpdate(placement.id, placement.priority, order)
                        }
                    todoDao.updateSortOrders(date, normalized, timeProvider.now())
                    Unit.success()
                }
            }

        override suspend fun moveIncompleteToDate(
            ids: Set<String>,
            targetDate: LocalDate,
        ): DataResult<List<DailyTodo>> {
            if (ids.isEmpty()) return emptyList<DailyTodo>().success()
            return databaseResult {
                database.withTransaction {
                    val selected =
                        ids.map { id -> todoDao.findById(id) ?: return@withTransaction notFound() }
                    if (selected.any { !it.status.isIncomplete }) return@withTransaction invalidTodo()
                    val nextOrders =
                        TodoPriority.entries
                            .associateWith { priority -> todoDao.nextSortOrder(targetDate, priority) }
                            .toMutableMap()
                    val now = timeProvider.now()
                    selected
                        .sortedWith(compareBy<TodoEntity>({ it.priority.ordinal }, TodoEntity::sortOrder))
                        .forEach { todo ->
                            val order = nextOrders.getValue(todo.priority)
                            nextOrders[todo.priority] = order + 1
                            check(
                                todoDao.updatePlacement(
                                    id = todo.id,
                                    date = targetDate,
                                    priority = todo.priority,
                                    sortOrder = order,
                                    updatedAt = now,
                                ) == 1,
                            )
                        }
                    selected.map { todo -> requireNotNull(todoDao.findById(todo.id)).toDomain() }.success()
                }
            }
        }

        override suspend fun bindContentBlock(
            id: String,
            blockId: String?,
        ): DataResult<DailyTodo> =
            databaseResult {
                database.withTransaction {
                    if (todoDao.findById(id) == null) return@withTransaction notFound()
                    check(todoDao.updateLinkedBlock(id, blockId, timeProvider.now()) == 1)
                    todoDao.findById(id)?.toDomain()?.success() ?: notFound()
                }
            }

        override suspend fun getStatsByDates(dates: Set<LocalDate>): DataResult<Map<LocalDate, TodoDateStats>> =
            if (dates.isEmpty()) {
                DataResult.Success(emptyMap())
            } else {
                databaseResult {
                    DataResult.Success(
                        todoDao
                            .getStatsByDates(dates)
                            .associate { row ->
                                row.scheduledDate to TodoDateStats(row.total, row.done)
                            },
                    )
                }
            }

        private suspend fun <T> databaseResult(operation: suspend () -> DataResult<T>): DataResult<T> =
            withContext(ioDispatcher) {
                try {
                    operation()
                } catch (_: SQLiteConstraintException) {
                    DataResult.Failure(DataError.Conflict)
                } catch (_: SQLiteException) {
                    DataResult.Failure(DataError.Storage)
                }
            }

        private fun Flow<DataResult<List<DailyTodo>>>.catchStorageErrors(): Flow<DataResult<List<DailyTodo>>> =
            catch {
                currentCoroutineContext().ensureActive()
                emit(DataResult.Failure(DataError.Storage))
            }
    }

private fun String?.normalized(maxLength: Int): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= maxLength }

private fun String.normalizedTitle(): String? =
    trim().takeIf { value ->
        value.isNotEmpty() &&
            value.length <= TODO_TITLE_MAX_LENGTH &&
            value.none(Char::isISOControl)
    }

private fun String?.isValidOptionalText(maxLength: Int): Boolean {
    val value = this?.trim() ?: return true
    return value.length <= maxLength &&
        value.none { character ->
            character.isISOControl() &&
                character != '\n' &&
                character != '\r' &&
                character != '\t'
        }
}

private fun <T> T.success(): DataResult<T> = DataResult.Success(this)

private fun <T> notFound(): DataResult<T> = DataResult.Failure(DataError.NotFound)

private fun <T> invalidTodo(): DataResult<T> =
    DataResult.Failure(DataError.Validation(DataValidationReason.INVALID_TODO))
