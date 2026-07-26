package com.worklogai.app.feature.todo

import androidx.lifecycle.SavedStateHandle
import com.worklogai.app.app.navigation.ENTRY_DATE_ARGUMENT
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import com.worklogai.app.core.repository.CreateTodoRequest
import com.worklogai.app.core.repository.TodoDateStats
import com.worklogai.app.core.repository.TodoPlacement
import com.worklogai.app.core.repository.TodoRepository
import com.worklogai.app.core.repository.UpdateTodoRequest
import com.worklogai.app.core.todo.CompleteTodoAndRecordUseCase
import com.worklogai.app.core.todo.CompletedTodoRecord
import com.worklogai.app.core.todo.CreateTodoFromTextBlockUseCase
import com.worklogai.app.feature.editor.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TodayTodoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val date = LocalDate.of(2026, 7, 24)
    private lateinit var repository: FakeTodoRepository
    private lateinit var complete: CompleteTodoAndRecordUseCase
    private lateinit var conversion: CreateTodoFromTextBlockUseCase
    private lateinit var viewModel: TodayTodoViewModel

    @Before
    fun setUp() {
        repository = FakeTodoRepository(date)
        complete = mockk()
        conversion = mockk()
        coEvery { complete(any(), any(), any()) } answers {
            val todo = repository.byId(firstArg())!!
            DataResult.Success(CompletedTodoRecord(todo.copy(status = TodoStatus.DONE), "block"))
        }
        coEvery { conversion(any(), any(), any(), any()) } returns DataResult.Failure(DataError.NotFound)
        viewModel =
            TodayTodoViewModel(
                repository,
                complete,
                conversion,
                fixedDateProvider(date),
                SavedStateHandle(),
            )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `initial state observes current date and quick add clears input`() {
        assertEquals(date, viewModel.uiState.value.date)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.onAction(TodayTodoAction.QuickTitleChanged("  整理接口  "))
        viewModel.onAction(TodayTodoAction.QuickAdd)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.quickTitle)
        assertEquals("整理接口", repository.current.single().title)
        assertEquals(TodoPriority.MEDIUM, repository.current.single().priority)
    }

    @Test
    fun `blank quick title is ignored`() {
        viewModel.onAction(TodayTodoAction.QuickTitleChanged("   "))
        viewModel.onAction(TodayTodoAction.QuickAdd)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.current.isEmpty())
    }

    @Test
    fun `toggle opens completion choice and complete only stores note`() {
        val todo = repository.add("待完成")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayTodoAction.ToggleDone(todo.id))
        assertNotNull(viewModel.uiState.value.completionPrompt)
        viewModel.onAction(TodayTodoAction.UpdateCompletionNote("已验收"))
        viewModel.onAction(TodayTodoAction.CompleteOnly)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.completionPrompt)
        assertEquals(TodoStatus.DONE, repository.byId(todo.id)?.status)
        assertEquals("已验收", repository.byId(todo.id)?.completionNote)
    }

    @Test
    fun `complete and record invokes atomic use case once and clears prompt`() {
        val todo = repository.add("完成并记录")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayTodoAction.ToggleDone(todo.id))
        viewModel.onAction(TodayTodoAction.UpdateCompletionNote("结果已确认"))
        viewModel.onAction(TodayTodoAction.CompleteAndRecord)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { complete(todo.id, "结果已确认", false) }
        assertNull(viewModel.uiState.value.completionPrompt)
    }

    @Test
    fun `status transitions and delete confirmation update repository explicitly`() {
        val todo = repository.add("状态流转")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayTodoAction.ChangeStatus(todo.id, TodoStatus.IN_PROGRESS))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TodoStatus.IN_PROGRESS, repository.byId(todo.id)?.status)

        viewModel.onAction(TodayTodoAction.ChangeStatus(todo.id, TodoStatus.CANCELED))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TodoStatus.CANCELED, repository.byId(todo.id)?.status)

        viewModel.onAction(TodayTodoAction.RequestDelete(todo.id))
        assertEquals(todo.id, viewModel.uiState.value.deleteConfirmationId)
        viewModel.onAction(TodayTodoAction.ConfirmDelete)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertNull(repository.byId(todo.id))
        assertNull(viewModel.uiState.value.deleteConfirmationId)
    }

    @Test
    fun `text conversion requires explicit duplicate confirmation`() {
        val created = repository.add("记录转待办", date.plusDays(1), TodoPriority.HIGH)
        coEvery { conversion("block", date, TodoPriority.MEDIUM, false) } returns
            DataResult.Failure(DataError.Conflict)
        coEvery { conversion("block", date, TodoPriority.MEDIUM, true) } returns DataResult.Success(created)

        viewModel.onAction(TodayTodoAction.RequestTextConversion("block"))
        viewModel.onAction(TodayTodoAction.UpdateConversion(requireNotNull(viewModel.uiState.value.conversionPrompt)))
        viewModel.onAction(TodayTodoAction.ConfirmTextConversion)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(requireNotNull(viewModel.uiState.value.conversionPrompt).duplicateWarning)
        viewModel.onAction(TodayTodoAction.ConfirmTextConversion)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { conversion("block", date, TodoPriority.MEDIUM, false) }
        coVerify(exactly = 1) { conversion("block", date, TodoPriority.MEDIUM, true) }
        assertNull(viewModel.uiState.value.conversionPrompt)
    }

    @Test
    fun `drag changes only temporary order until commit then persists once`() {
        val first = repository.add("A")
        val second = repository.add("B")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayTodoAction.StartDrag(first.id))
        viewModel.onAction(TodayTodoAction.MoveDragged(1))

        assertEquals(
            listOf(second.id, first.id),
            viewModel.uiState.value.incompleteTodos
                .map { it.id },
        )
        assertEquals(0, repository.reorderCount)

        viewModel.onAction(TodayTodoAction.CommitDrag)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.reorderCount)
        assertEquals(listOf(second.id, first.id), repository.current.map { it.id })
    }

    @Test
    fun `drag across a priority boundary updates priority and persists once`() {
        val urgent = repository.add("紧急事项", priority = TodoPriority.URGENT)
        val medium = repository.add("普通事项", priority = TodoPriority.MEDIUM)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayTodoAction.StartDrag(urgent.id))
        viewModel.onAction(TodayTodoAction.MoveDragged(1))
        viewModel.onAction(TodayTodoAction.CommitDrag)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TodoPriority.MEDIUM, repository.byId(urgent.id)?.priority)
        assertEquals(listOf(medium.id, urgent.id), repository.current.map { it.id })
        assertEquals(1, repository.reorderCount)
    }

    @Test
    fun `saving done from editor still requires an explicit completion choice`() {
        val todo = repository.add("需要确认")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayTodoAction.OpenEditor(todo.id))
        val draft = requireNotNull(viewModel.uiState.value.editor).copy(status = TodoStatus.DONE)
        viewModel.onAction(TodayTodoAction.UpdateEditor(draft))
        viewModel.onAction(TodayTodoAction.SaveEditor)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TodoStatus.NOT_STARTED, repository.byId(todo.id)?.status)
        assertEquals(
            todo.id,
            viewModel.uiState.value.completionPrompt
                ?.todoId,
        )
    }

    @Test
    fun `older incomplete todos move in one repository operation`() {
        val older = repository.add("昨日遗留", date.minusDays(1))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertEquals(1, viewModel.uiState.value.olderIncomplete.size)

        viewModel.onAction(TodayTodoAction.MoveAllOlderToToday)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(date, repository.byId(older.id)?.scheduledDate)
        assertTrue(
            viewModel.uiState.value.olderIncomplete
                .isEmpty(),
        )
    }

    @Test
    fun `editor saves explicit future date and priority`() {
        viewModel.onAction(TodayTodoAction.OpenEditor())
        val draft =
            requireNotNull(viewModel.uiState.value.editor).copy(
                title = "明日计划",
                scheduledDate = date.plusDays(1),
                priority = TodoPriority.HIGH,
            )
        viewModel.onAction(TodayTodoAction.UpdateEditor(draft))
        viewModel.onAction(TodayTodoAction.SaveEditor)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val created = repository.all.single()
        assertEquals(date.plusDays(1), created.scheduledDate)
        assertEquals(TodoPriority.HIGH, created.priority)
    }

    @Test
    fun `fixed future date remains date scoped`() {
        val future = date.plusDays(5)
        val fixed =
            TodayTodoViewModel(
                repository,
                complete,
                conversion,
                fixedDateProvider(date),
                SavedStateHandle(mapOf(ENTRY_DATE_ARGUMENT to future.toString())),
            )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(future, fixed.uiState.value.date)
        fixed.onAction(TodayTodoAction.DateChanged(date))
        assertEquals(future, fixed.uiState.value.date)
    }

    private fun fixedDateProvider(today: LocalDate): LocalDateProvider =
        object : LocalDateProvider {
            override fun today(): LocalDate = today

            override fun zoneId(): ZoneId = ZoneId.of("Asia/Shanghai")
        }

    private class FakeTodoRepository(
        private val observedDate: LocalDate,
    ) : TodoRepository {
        private val allItems = mutableListOf<DailyTodo>()
        private val dateFlow = MutableStateFlow<DataResult<List<DailyTodo>>>(DataResult.Success(emptyList()))
        private val olderFlow = MutableStateFlow<DataResult<List<DailyTodo>>>(DataResult.Success(emptyList()))
        var reorderCount = 0
        private var nextId = 1

        val all: List<DailyTodo> get() = allItems.toList()
        val current: List<DailyTodo> get() = allItems.filter { it.scheduledDate == observedDate }

        fun add(
            title: String,
            date: LocalDate = observedDate,
            priority: TodoPriority = TodoPriority.MEDIUM,
        ): DailyTodo =
            createTodo("todo-${nextId++}", title, date, priority).also {
                allItems += it
                emit()
            }

        fun byId(id: String): DailyTodo? = allItems.firstOrNull { it.id == id }

        override fun observeByDate(date: LocalDate): Flow<DataResult<List<DailyTodo>>> = dateFlow

        override fun observeIncompleteBefore(date: LocalDate): Flow<DataResult<List<DailyTodo>>> = olderFlow

        override suspend fun getByDate(date: LocalDate): DataResult<List<DailyTodo>> =
            DataResult.Success(allItems.filter { it.scheduledDate == date })

        override suspend fun getById(id: String): DataResult<DailyTodo> =
            byId(id)?.let { DataResult.Success(it) } ?: DataResult.Failure(DataError.NotFound)

        override suspend fun create(request: CreateTodoRequest): DataResult<DailyTodo> =
            DataResult.Success(
                add(request.title.trim(), request.scheduledDate)
                    .copy(
                        note = request.note?.trim(),
                        priority = request.priority,
                    ).also { replace(it) },
            )

        override suspend fun update(request: UpdateTodoRequest): DataResult<DailyTodo> {
            val old = byId(request.id) ?: return DataResult.Failure(DataError.NotFound)
            val updated =
                old.copy(
                    scheduledDate = request.scheduledDate,
                    title = request.title.trim(),
                    note = request.note,
                    priority = request.priority,
                    status = request.status,
                    completionNote = request.completionNote,
                )
            replace(updated)
            return DataResult.Success(updated)
        }

        override suspend fun delete(id: String): DataResult<Unit> {
            allItems.removeAll { it.id == id }
            emit()
            return DataResult.Success(Unit)
        }

        override suspend fun updateStatus(
            id: String,
            status: TodoStatus,
            completionNote: String?,
        ): DataResult<DailyTodo> {
            val old = byId(id) ?: return DataResult.Failure(DataError.NotFound)
            val updated =
                old.copy(
                    status = status,
                    completionNote = completionNote ?: old.completionNote,
                    completedAt = if (status == TodoStatus.DONE) NOW else null,
                )
            replace(updated)
            return DataResult.Success(updated)
        }

        override suspend fun reorder(
            date: LocalDate,
            placements: List<TodoPlacement>,
        ): DataResult<Unit> {
            reorderCount++
            val completed = allItems.filter { it.scheduledDate == date && it.status == TodoStatus.DONE }
            val reordered =
                placements.mapIndexed { index, placement ->
                    requireNotNull(byId(placement.id)).copy(priority = placement.priority, sortOrder = index)
                }
            allItems.removeAll { it.scheduledDate == date }
            allItems.addAll(reordered + completed)
            emit()
            return DataResult.Success(Unit)
        }

        override suspend fun moveIncompleteToDate(
            ids: Set<String>,
            targetDate: LocalDate,
        ): DataResult<List<DailyTodo>> {
            val moved =
                allItems.filter { it.id in ids }.map { it.copy(scheduledDate = targetDate) }
            moved.forEach(::replace)
            return DataResult.Success(moved)
        }

        override suspend fun bindContentBlock(
            id: String,
            blockId: String?,
        ): DataResult<DailyTodo> {
            val updated = requireNotNull(byId(id)).copy(linkedContentBlockId = blockId)
            replace(updated)
            return DataResult.Success(updated)
        }

        override suspend fun getStatsByDates(dates: Set<LocalDate>): DataResult<Map<LocalDate, TodoDateStats>> =
            DataResult.Success(emptyMap())

        private fun replace(todo: DailyTodo) {
            allItems.replaceAll { if (it.id == todo.id) todo else it }
            emit()
        }

        private fun emit() {
            dateFlow.value =
                DataResult.Success(
                    allItems
                        .filter { it.scheduledDate == observedDate }
                        .sortedBy { it.sortOrder },
                )
            olderFlow.value =
                DataResult.Success(
                    allItems.filter {
                        it.scheduledDate < observedDate &&
                            (it.status == TodoStatus.NOT_STARTED || it.status == TodoStatus.IN_PROGRESS)
                    },
                )
        }

        private fun createTodo(
            id: String,
            title: String,
            date: LocalDate,
            priority: TodoPriority,
        ) = DailyTodo(
            id,
            date,
            title,
            null,
            priority,
            TodoStatus.NOT_STARTED,
            allItems.count { it.scheduledDate == date },
            null,
            null,
            NOW,
            NOW,
            null,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-24T08:00:00Z")
    }
}
