package com.worklogai.app.core.todo

import androidx.room.withTransaction
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.dao.ContentBlockDao
import com.worklogai.app.core.database.dao.TodoDao
import com.worklogai.app.core.database.dao.WorkEntryDao
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.mapper.toDomain
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TODO_COMPLETION_NOTE_MAX_LENGTH
import com.worklogai.app.core.model.TODO_NOTE_MAX_LENGTH
import com.worklogai.app.core.model.TODO_TITLE_MAX_LENGTH
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import com.worklogai.app.core.repository.CreateTodoRequest
import com.worklogai.app.core.repository.TodoPlacement
import com.worklogai.app.core.repository.TodoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class CompletedTodoRecord(
    val todo: DailyTodo,
    val contentBlockId: String,
)

@Suppress("LongParameterList")
class CompleteTodoAndRecordUseCase
    @Inject
    constructor(
        private val database: WorkLogDatabase,
        private val todoDao: TodoDao,
        private val workEntryDao: WorkEntryDao,
        private val contentBlockDao: ContentBlockDao,
        private val idGenerator: IdGenerator,
        private val timeProvider: TimeProvider,
        private val localDateProvider: LocalDateProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(
            todoId: String,
            completionNote: String?,
            resync: Boolean = false,
        ): DataResult<CompletedTodoRecord> =
            withContext(ioDispatcher) {
                if (completionNote?.trim()?.length.orZero() >
                    TODO_COMPLETION_NOTE_MAX_LENGTH
                ) {
                    return@withContext invalid()
                }
                try {
                    completeInTransaction(todoId, completionNote, resync)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: android.database.sqlite.SQLiteException) {
                    DataResult.Failure(DataError.Storage)
                } catch (_: IllegalStateException) {
                    DataResult.Failure(DataError.Storage)
                }
            }

        private suspend fun completeInTransaction(
            todoId: String,
            completionNote: String?,
            resync: Boolean,
        ): DataResult<CompletedTodoRecord> =
            database.withTransaction {
                val todo = todoDao.findById(todoId) ?: return@withTransaction notFound()
                if (todo.scheduledDate > localDateProvider.today()) {
                    return@withTransaction DataResult.Failure(
                        DataError.Validation(DataValidationReason.FUTURE_WORK_ENTRY),
                    )
                }
                if (todo.linkedContentBlockId != null && !resync) {
                    return@withTransaction DataResult.Failure(
                        DataError.Validation(DataValidationReason.ALREADY_LINKED),
                    )
                }
                val now = timeProvider.now()
                val entryRelation = workEntryDao.getWithContentByDateIncludingDeleted(todo.scheduledDate)
                val entryId =
                    when {
                        entryRelation == null -> {
                            val id = idGenerator.generate()
                            workEntryDao.insert(
                                WorkEntryEntity(
                                    id = id,
                                    entryDate = todo.scheduledDate,
                                    title = null,
                                    allowAiProcessing = true,
                                    isDeleted = false,
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            )
                            id
                        }
                        entryRelation.entry.isDeleted -> {
                            workEntryDao.restore(entryRelation.entry.id, now)
                            entryRelation.entry.id
                        }
                        else -> entryRelation.entry.id
                    }
                val blockId = idGenerator.generate()
                val normalizedNote = completionNote.normalized(TODO_COMPLETION_NOTE_MAX_LENGTH)
                val text =
                    buildString {
                        append("已完成：")
                        append(todo.title)
                        normalizedNote?.let {
                            append("\n\n")
                            append(it)
                        }
                    }
                contentBlockDao.insert(
                    ContentBlockEntity(
                        id = blockId,
                        entryId = entryId,
                        blockType = ContentBlockType.TEXT,
                        blockOrder = contentBlockDao.nextBlockOrder(entryId),
                        textContent = text,
                        structuredContent = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                val updated =
                    todo.copy(
                        status = TodoStatus.DONE,
                        completionNote = normalizedNote ?: todo.completionNote,
                        linkedContentBlockId = blockId,
                        completedAt = todo.completedAt ?: now,
                        updatedAt = now,
                    )
                todoDao.update(updated)
                DataResult.Success(CompletedTodoRecord(updated.toDomain(), blockId))
            }
    }

class CreateTodoFromTextBlockUseCase
    @Inject
    constructor(
        private val contentBlockDao: ContentBlockDao,
        private val todoDao: TodoDao,
        private val todoRepository: TodoRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(
            blockId: String,
            scheduledDate: LocalDate,
            priority: TodoPriority,
            confirmDuplicate: Boolean,
        ): DataResult<DailyTodo> =
            withContext(ioDispatcher) {
                val block = contentBlockDao.getById(blockId) ?: return@withContext notFound()
                if (block.blockType != ContentBlockType.TEXT || block.textContent.isNullOrBlank()) {
                    return@withContext invalid()
                }
                val lines =
                    block.textContent
                        .lineSequence()
                        .map(String::trim)
                        .toList()
                val firstIndex = lines.indexOfFirst(String::isNotEmpty)
                if (firstIndex < 0) return@withContext invalid()
                val title = lines[firstIndex].take(TODO_TITLE_MAX_LENGTH)
                val note =
                    lines
                        .drop(firstIndex + 1)
                        .joinToString("\n")
                        .trim()
                        .take(TODO_NOTE_MAX_LENGTH)
                        .takeIf(String::isNotEmpty)
                if (!confirmDuplicate && todoDao.countByDateAndTitle(scheduledDate, title) > 0) {
                    return@withContext DataResult.Failure(DataError.Conflict)
                }
                todoRepository.create(CreateTodoRequest(scheduledDate, title, note, priority))
            }
    }

class ReorderTodosUseCase
    @Inject
    constructor(
        private val repository: TodoRepository,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            placements: List<TodoPlacement>,
        ): DataResult<Unit> = repository.reorder(date, placements)
    }

class MoveTodosToDateUseCase
    @Inject
    constructor(
        private val repository: TodoRepository,
    ) {
        suspend operator fun invoke(
            ids: Set<String>,
            targetDate: LocalDate,
        ): DataResult<List<DailyTodo>> = repository.moveIncompleteToDate(ids, targetDate)
    }

private fun String?.normalized(maxLength: Int): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= maxLength }

private fun Int?.orZero(): Int = this ?: 0

private fun <T> invalid(): DataResult<T> = DataResult.Failure(DataError.Validation(DataValidationReason.INVALID_TODO))

private fun <T> notFound(): DataResult<T> = DataResult.Failure(DataError.NotFound)
