package com.worklogai.app.core.database.repository

import android.app.Application
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.database.FixedTimeProvider
import com.worklogai.app.core.database.SequenceIdGenerator
import com.worklogai.app.core.database.createInMemoryDatabase
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.failureValue
import com.worklogai.app.core.database.successValue
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import com.worklogai.app.core.repository.CreateTodoRequest
import com.worklogai.app.core.repository.TodoPlacement
import com.worklogai.app.core.repository.UpdateTodoRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflineTodoRepositoryTest {
    private lateinit var database: com.worklogai.app.core.database.WorkLogDatabase
    private lateinit var repository: OfflineTodoRepository
    private val date = LocalDate.of(2026, 7, 24)

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository =
            OfflineTodoRepository(
                database,
                database.todoDao(),
                SequenceIdGenerator((1..30).map { "todo-$it" }),
                FixedTimeProvider(NOW),
                Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create trims content and orders incomplete todos by priority then manual order`() =
        runBlocking {
            val medium =
                repository
                    .create(CreateTodoRequest(date, "  整理文档  ", "  补充接口说明  "))
                    .successValue()
            val urgent =
                repository
                    .create(CreateTodoRequest(date, "修复阻断问题", priority = TodoPriority.URGENT))
                    .successValue()
            val high =
                repository
                    .create(CreateTodoRequest(date, "联调", priority = TodoPriority.HIGH))
                    .successValue()

            assertEquals("整理文档", medium.title)
            assertEquals("补充接口说明", medium.note)
            assertEquals(
                listOf(urgent.id, high.id, medium.id),
                repository.getByDate(date).successValue().map { it.id },
            )
            assertEquals(
                repository.getByDate(date).successValue(),
                repository.observeByDate(date).first().successValue(),
            )
        }

    @Test
    fun `status transitions set and clear completed time while completed items sort last`() =
        runBlocking {
            val first =
                repository
                    .create(CreateTodoRequest(date, "第一项", priority = TodoPriority.URGENT))
                    .successValue()
            val second =
                repository
                    .create(CreateTodoRequest(date, "第二项", priority = TodoPriority.LOW))
                    .successValue()

            val completed = repository.updateStatus(first.id, TodoStatus.DONE, "已验证").successValue()
            assertEquals("已验证", completed.completionNote)
            assertEquals(NOW, completed.completedAt)
            assertEquals(listOf(second.id, first.id), repository.getByDate(date).successValue().map { it.id })

            val reopened = repository.updateStatus(first.id, TodoStatus.IN_PROGRESS).successValue()
            assertNull(reopened.completedAt)
            assertEquals("已验证", reopened.completionNote)
        }

    @Test
    fun `reorder normalizes sort order and supports moving across priorities`() =
        runBlocking {
            val first = repository.create(CreateTodoRequest(date, "A")).successValue()
            val second = repository.create(CreateTodoRequest(date, "B")).successValue()
            val third = repository.create(CreateTodoRequest(date, "C")).successValue()

            repository
                .reorder(
                    date,
                    listOf(
                        TodoPlacement(third.id, TodoPriority.URGENT),
                        TodoPlacement(second.id, TodoPriority.MEDIUM),
                        TodoPlacement(first.id, TodoPriority.MEDIUM),
                    ),
                ).successValue()

            val loaded = repository.getByDate(date).successValue()
            assertEquals(listOf(third.id, second.id, first.id), loaded.map { it.id })
            assertEquals(listOf(0, 0, 1), loaded.map { it.sortOrder })
            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_ORDERING),
                repository
                    .reorder(date, listOf(TodoPlacement(first.id, TodoPriority.LOW)))
                    .failureValue(),
            )
        }

    @Test
    fun `batch move is atomic and appends within each target priority`() =
        runBlocking {
            val yesterday = date.minusDays(1)
            val existing =
                repository
                    .create(CreateTodoRequest(date, "已有", priority = TodoPriority.HIGH))
                    .successValue()
            val first =
                repository
                    .create(CreateTodoRequest(yesterday, "迁移一", priority = TodoPriority.HIGH))
                    .successValue()
            val second =
                repository
                    .create(CreateTodoRequest(yesterday, "迁移二", priority = TodoPriority.HIGH))
                    .successValue()
            val done =
                repository
                    .create(CreateTodoRequest(yesterday, "已完成"))
                    .successValue()
                    .let { repository.updateStatus(it.id, TodoStatus.DONE).successValue() }

            val moved = repository.moveIncompleteToDate(setOf(first.id, second.id), date).successValue()
            assertEquals(setOf(first.id, second.id), moved.map { it.id }.toSet())
            assertEquals(
                listOf(existing.id, first.id, second.id),
                repository.getByDate(date).successValue().map { it.id },
            )
            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_TODO),
                repository.moveIncompleteToDate(setOf(done.id), date).failureValue(),
            )
            assertEquals(yesterday, repository.getById(done.id).successValue().scheduledDate)
        }

    @Test
    fun `mixed valid and completed batch move is rejected atomically`() =
        runBlocking {
            val yesterday = date.minusDays(1)
            val active = repository.create(CreateTodoRequest(yesterday, "仍需处理")).successValue()
            val done =
                repository
                    .create(CreateTodoRequest(yesterday, "已经完成"))
                    .successValue()
                    .let { repository.updateStatus(it.id, TodoStatus.DONE).successValue() }

            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_TODO),
                repository.moveIncompleteToDate(setOf(active.id, done.id), date).failureValue(),
            )
            assertEquals(yesterday, repository.getById(active.id).successValue().scheduledDate)
            assertEquals(yesterday, repository.getById(done.id).successValue().scheduledDate)
            assertTrue(repository.getByDate(date).successValue().isEmpty())
        }

    @Test
    fun `edit updates fields and appends todo to the target priority`() =
        runBlocking {
            val targetDate = date.plusDays(1)
            val existing =
                repository
                    .create(CreateTodoRequest(targetDate, "已有", priority = TodoPriority.HIGH))
                    .successValue()
            val todo = repository.create(CreateTodoRequest(date, "旧标题")).successValue()

            val updated =
                repository
                    .update(
                        UpdateTodoRequest(
                            id = todo.id,
                            scheduledDate = targetDate,
                            title = "  新标题  ",
                            note = "  新备注  ",
                            priority = TodoPriority.HIGH,
                            status = TodoStatus.IN_PROGRESS,
                            completionNote = null,
                        ),
                    ).successValue()

            assertEquals("新标题", updated.title)
            assertEquals("新备注", updated.note)
            assertEquals(TodoStatus.IN_PROGRESS, updated.status)
            assertEquals(targetDate, updated.scheduledDate)
            assertEquals(listOf(existing.id, updated.id), repository.getByDate(targetDate).successValue().map { it.id })
            assertEquals(1, updated.sortOrder)
        }

    @Test
    fun `deleting a linked todo preserves its historical content block`() =
        runBlocking {
            database.workEntryDao().insert(WorkEntryEntity("entry", date, null, true, false, NOW, NOW))
            database.contentBlockDao().insert(
                ContentBlockEntity("block", "entry", ContentBlockType.TEXT, 0, "历史事实", null, NOW, NOW),
            )
            val todo = repository.create(CreateTodoRequest(date, "可删除待办")).successValue()
            repository.bindContentBlock(todo.id, "block").successValue()

            repository.delete(todo.id).successValue()

            assertEquals(DataError.NotFound, repository.getById(todo.id).failureValue())
            assertNotNull(database.contentBlockDao().getById("block"))
        }

    @Test
    fun `date stats and older incomplete query exclude canceled and completed items`() =
        runBlocking {
            val yesterday = date.minusDays(1)
            val active = repository.create(CreateTodoRequest(yesterday, "未开始")).successValue()
            repository
                .create(CreateTodoRequest(yesterday, "已完成"))
                .successValue()
                .let { repository.updateStatus(it.id, TodoStatus.DONE).successValue() }
            repository
                .create(CreateTodoRequest(yesterday, "已取消"))
                .successValue()
                .let { repository.updateStatus(it.id, TodoStatus.CANCELED).successValue() }

            assertEquals(
                listOf(active.id),
                repository
                    .observeIncompleteBefore(date)
                    .first()
                    .successValue()
                    .map { it.id },
            )
            val stats = repository.getStatsByDates(setOf(yesterday)).successValue().getValue(yesterday)
            assertEquals(3, stats.total)
            assertEquals(1, stats.done)
        }

    @Test
    fun `deleting linked content block clears link without deleting todo`() =
        runBlocking {
            database.workEntryDao().insert(
                WorkEntryEntity("entry", date, null, true, false, NOW, NOW),
            )
            database.contentBlockDao().insert(
                ContentBlockEntity(
                    "block",
                    "entry",
                    ContentBlockType.TEXT,
                    0,
                    "记录",
                    null,
                    NOW,
                    NOW,
                ),
            )
            val todo = repository.create(CreateTodoRequest(date, "关联项")).successValue()
            repository.bindContentBlock(todo.id, "block").successValue()

            database.contentBlockDao().delete(requireNotNull(database.contentBlockDao().getById("block")))

            assertNull(repository.getById(todo.id).successValue().linkedContentBlockId)
            assertTrue(database.todoDao().getAll().any { it.id == todo.id })
        }

    @Test
    fun `blank and overlong titles are rejected without database writes`() =
        runBlocking {
            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_TODO),
                repository.create(CreateTodoRequest(date, "   ")).failureValue(),
            )
            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_TODO),
                repository.create(CreateTodoRequest(date, "x".repeat(201))).failureValue(),
            )
            assertTrue(database.todoDao().getAll().isEmpty())
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-24T08:00:00Z")
    }
}
