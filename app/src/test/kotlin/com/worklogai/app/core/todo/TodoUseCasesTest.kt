package com.worklogai.app.core.todo

import android.app.Application
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.database.FixedTimeProvider
import com.worklogai.app.core.database.SequenceIdGenerator
import com.worklogai.app.core.database.createInMemoryDatabase
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.failureValue
import com.worklogai.app.core.database.repository.OfflineTodoRepository
import com.worklogai.app.core.database.successValue
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import com.worklogai.app.core.repository.CreateTodoRequest
import kotlinx.coroutines.Dispatchers
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
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TodoUseCasesTest {
    private lateinit var database: com.worklogai.app.core.database.WorkLogDatabase
    private lateinit var repository: OfflineTodoRepository
    private lateinit var completeAndRecord: CompleteTodoAndRecordUseCase
    private val date = LocalDate.of(2026, 7, 24)

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository =
            OfflineTodoRepository(
                database,
                database.todoDao(),
                SequenceIdGenerator((1..20).map { "todo-$it" }),
                FixedTimeProvider(NOW),
                Dispatchers.Unconfined,
            )
        completeAndRecord =
            CompleteTodoAndRecordUseCase(
                database,
                database.todoDao(),
                database.workEntryDao(),
                database.contentBlockDao(),
                SequenceIdGenerator(listOf("entry-created", "block-created", "block-next")),
                FixedTimeProvider(NOW),
                fixedDateProvider(date),
                Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `completion creates missing work entry text block and atomic todo link`() =
        runBlocking {
            val todo = repository.create(CreateTodoRequest(date, "完成登录联调")).successValue()

            val result = completeAndRecord(todo.id, "接口与错误分支均通过").successValue()

            assertEquals(TodoStatus.DONE, result.todo.status)
            assertEquals("block-created", result.todo.linkedContentBlockId)
            val entry = database.workEntryDao().getActiveWithContentByDate(date)
            assertNotNull(entry)
            assertEquals(
                "已完成：完成登录联调\n\n接口与错误分支均通过",
                entry!!
                    .blocks
                    .single()
                    .block.textContent,
            )
            assertEquals(
                0,
                entry.blocks
                    .single()
                    .block.blockOrder,
            )
        }

    @Test
    fun `completion appends to existing entry and rejects duplicate synchronization`() =
        runBlocking {
            database.workEntryDao().insert(WorkEntryEntity("entry", date, null, true, false, NOW, NOW))
            database.contentBlockDao().insert(
                ContentBlockEntity("old", "entry", ContentBlockType.TEXT, 0, "原记录", null, NOW, NOW),
            )
            val todo = repository.create(CreateTodoRequest(date, "联调")).successValue()

            val first = completeAndRecord(todo.id, null).successValue()
            assertEquals(1, database.contentBlockDao().getById(first.contentBlockId)!!.blockOrder)
            assertEquals(
                DataError.Validation(DataValidationReason.ALREADY_LINKED),
                completeAndRecord(todo.id, null).failureValue(),
            )
            assertEquals(2, database.contentBlockDao().getByEntryId("entry").size)
        }

    @Test
    fun `resynchronization creates a new block and preserves the old work record`() =
        runBlocking {
            database.workEntryDao().insert(WorkEntryEntity("entry", date, null, true, false, NOW, NOW))
            val todo = repository.create(CreateTodoRequest(date, "联调")).successValue()

            val first = completeAndRecord(todo.id, "第一次").successValue()
            val second = completeAndRecord(todo.id, "第二次", resync = true).successValue()

            assertEquals("block-created", second.contentBlockId)
            assertEquals(second.contentBlockId, repository.getById(todo.id).successValue().linkedContentBlockId)
            assertEquals(
                setOf(first.contentBlockId, second.contentBlockId),
                database
                    .contentBlockDao()
                    .getByEntryId("entry")
                    .map { it.id }
                    .toSet(),
            )
            assertEquals(
                listOf("已完成：联调\n\n第一次", "已完成：联调\n\n第二次"),
                database
                    .contentBlockDao()
                    .getByEntryId("entry")
                    .sortedBy { it.blockOrder }
                    .map { it.textContent },
            )
        }

    @Test
    fun `content block insert failure rolls back todo completion and returns storage error`() =
        runBlocking {
            database.workEntryDao().insert(WorkEntryEntity("entry", date, null, true, false, NOW, NOW))
            database.contentBlockDao().insert(
                ContentBlockEntity(
                    "entry-created",
                    "entry",
                    ContentBlockType.TEXT,
                    0,
                    "占用即将生成的块 ID",
                    null,
                    NOW,
                    NOW,
                ),
            )
            val todo = repository.create(CreateTodoRequest(date, "不可部分完成")).successValue()

            assertEquals(DataError.Storage, completeAndRecord(todo.id, "不会保存").failureValue())
            val unchanged = repository.getById(todo.id).successValue()
            assertEquals(TodoStatus.NOT_STARTED, unchanged.status)
            assertNull(unchanged.completedAt)
            assertNull(unchanged.linkedContentBlockId)
            assertEquals(1, database.contentBlockDao().getByEntryId("entry").size)
        }

    @Test
    fun `future todo cannot create a future work record`() =
        runBlocking {
            val todo = repository.create(CreateTodoRequest(date.plusDays(1), "明日计划")).successValue()

            assertEquals(
                DataError.Validation(DataValidationReason.FUTURE_WORK_ENTRY),
                completeAndRecord(todo.id, null).failureValue(),
            )
            assertNull(database.workEntryDao().getActiveWithContentByDate(date.plusDays(1)))
            assertNull(repository.getById(todo.id).successValue().linkedContentBlockId)
        }

    @Test
    fun `text block conversion uses first line and requires duplicate confirmation`() =
        runBlocking {
            database.workEntryDao().insert(WorkEntryEntity("entry", date, null, true, false, NOW, NOW))
            database.contentBlockDao().insert(
                ContentBlockEntity(
                    "source",
                    "entry",
                    ContentBlockType.TEXT,
                    0,
                    "\n整理接口文档\n补充示例\n确认错误码",
                    null,
                    NOW,
                    NOW,
                ),
            )
            val useCase =
                CreateTodoFromTextBlockUseCase(
                    database.contentBlockDao(),
                    database.todoDao(),
                    repository,
                    Dispatchers.Unconfined,
                )

            val created = useCase("source", date.plusDays(1), TodoPriority.HIGH, false).successValue()
            assertEquals("整理接口文档", created.title)
            assertEquals("补充示例\n确认错误码", created.note)
            assertEquals(
                DataError.Conflict,
                useCase("source", date.plusDays(1), TodoPriority.HIGH, false).failureValue(),
            )
            assertTrue(
                useCase("source", date.plusDays(1), TodoPriority.HIGH, true) is
                    com.worklogai.app.core.common.result.DataResult.Success,
            )
        }

    private fun fixedDateProvider(today: LocalDate): LocalDateProvider =
        object : LocalDateProvider {
            override fun today(): LocalDate = today

            override fun zoneId(): ZoneId = ZoneId.of("Asia/Shanghai")
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-24T08:00:00Z")
    }
}
