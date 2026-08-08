package com.worklogai.app.core.database.repository

import android.app.Application
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.database.FixedTimeProvider
import com.worklogai.app.core.database.SequenceIdGenerator
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.createInMemoryDatabase
import com.worklogai.app.core.database.failureValue
import com.worklogai.app.core.database.successValue
import com.worklogai.app.core.model.AttachmentDraft
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.NewWorkContent
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class OfflineWorkEntryRepositoryTest {
    private val date = LocalDate.of(2026, 7, 6)
    private lateinit var database: com.worklogai.app.core.database.WorkLogDatabase
    private lateinit var repository: OfflineWorkEntryRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository =
            OfflineWorkEntryRepository(
                database = database,
                workEntryDao = database.workEntryDao(),
                contentBlockDao = database.contentBlockDao(),
                attachmentDao = database.attachmentDao(),
                tableContentCodec = KotlinxTableContentCodec(),
                idGenerator = SequenceIdGenerator((1..20).map { "id-$it" }),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `get or create returns existing entry and restores soft deleted entry`() {
        runBlocking {
            val created = repository.getOrCreateEntry(date).successValue()
            assertEquals("id-1", created.id)

            assertEquals("id-1", repository.getOrCreateEntry(date).successValue().id)
            repository.updateEntryTitle(created.id, "  登录模块  ").successValue()
            assertEquals("登录模块", repository.getEntry(date).successValue()?.title)

            repository.softDeleteEntry(created.id).successValue()
            assertNull(repository.getEntry(date).successValue())

            val restored = repository.getOrCreateEntry(date).successValue()
            assertEquals(created.id, restored.id)
            assertFalse(restored.isDeleted)
            assertEquals(
                restored.id,
                repository
                    .observeEntry(date)
                    .first()
                    .successValue()
                    ?.id,
            )
        }
    }

    @Test
    fun `repository manages blocks ordering attachments and purge paths atomically`() {
        runBlocking {
            val entry = repository.getOrCreateEntry(date).successValue()
            val text = repository.addTextBlock(entry.id, "初始记录").successValue()
            val table = repository.addTableBlock(entry.id, tableContent()).successValue()
            val image = repository.addImageBlock(entry.id).successValue()

            repository.updateTextBlock(text.id, "更新后的记录").successValue()
            repository.updateTableBlock(table.id, tableContent("测试结果")).successValue()
            repository.reorderBlocks(entry.id, listOf(image.id, table.id, text.id)).successValue()

            val loaded = repository.getEntry(date).successValue()!!
            assertEquals(listOf(image.id, table.id, text.id), loaded.blocks.map { it.id })
            assertEquals("更新后的记录", (loaded.blocks.last() as ContentBlock.Text).content)
            assertEquals("测试结果", (loaded.blocks[1] as ContentBlock.Table).content.title)

            val duplicateOrder = repository.reorderBlocks(entry.id, listOf(image.id, image.id, text.id))
            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_ORDERING),
                duplicateOrder.failureValue(),
            )
            val incompleteOrder = repository.reorderBlocks(entry.id, listOf(image.id, table.id))
            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_ORDERING),
                incompleteOrder.failureValue(),
            )

            repository
                .addAttachment(
                    image.id,
                    AttachmentDraft(
                        localPath = "attachments/one.jpg",
                        mimeType = "image/jpeg",
                        fileSize = 42L,
                        caption = "联调截图",
                    ),
                ).successValue()
            assertEquals(listOf("attachments/one.jpg"), repository.deleteBlock(image.id).successValue())

            val secondImage = repository.addImageBlock(entry.id).successValue()
            repository
                .addAttachment(
                    secondImage.id,
                    AttachmentDraft(
                        localPath = "attachments/two.jpg",
                        mimeType = "image/jpeg",
                        fileSize = 84L,
                    ),
                ).successValue()

            assertEquals(listOf("attachments/two.jpg"), repository.purgeEntry(entry.id).successValue())
            assertNull(repository.getEntry(date).successValue())
            assertTrue(database.contentBlockDao().getByEntryId(entry.id).isEmpty())
        }
    }

    @Test
    fun `image block creation stores attachment and caption in one aggregate`() {
        runBlocking {
            val entry = repository.getOrCreateEntry(date).successValue()
            val image =
                repository
                    .addImageBlock(
                        entry.id,
                        AttachmentDraft(
                            localPath = "images/imported.jpg",
                            mimeType = "image/jpeg",
                            fileSize = 128L,
                            width = 100,
                            height = 80,
                            caption = "初始说明",
                        ),
                    ).successValue()

            val attachment = image.attachments.single()
            assertEquals("初始说明", attachment.caption)
            assertEquals("images/imported.jpg", attachment.localPath)

            repository.updateImageCaption(attachment.id, "更新说明").successValue()
            val loaded =
                repository
                    .getEntry(date)
                    .successValue()!!
                    .blocks
                    .single() as ContentBlock.Image
            assertEquals("更新说明", loaded.attachments.single().caption)
        }
    }

    @Test
    fun `attachment insert failure rolls back the image block transaction`() {
        val collisionRepository =
            OfflineWorkEntryRepository(
                database = database,
                workEntryDao = database.workEntryDao(),
                contentBlockDao = database.contentBlockDao(),
                attachmentDao = database.attachmentDao(),
                tableContentCodec = KotlinxTableContentCodec(),
                idGenerator =
                    SequenceIdGenerator(
                        listOf("entry", "image-one", "attachment", "image-two", "attachment"),
                    ),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
        runBlocking {
            val entry = collisionRepository.getOrCreateEntry(date).successValue()
            val draft =
                AttachmentDraft(
                    localPath = "images/imported.jpg",
                    mimeType = "image/jpeg",
                    fileSize = 128L,
                    width = 100,
                    height = 80,
                )
            collisionRepository.addImageBlock(entry.id, draft).successValue()

            val result = collisionRepository.addImageBlock(entry.id, draft)

            assertEquals(DataError.Conflict, result.failureValue())
            assertEquals(
                1,
                collisionRepository
                    .getEntry(date)
                    .successValue()!!
                    .blocks.size,
            )
            assertEquals(1, database.attachmentDao().getPathsByEntryId(entry.id).size)
        }
    }

    @Test
    fun `creating first text for a date atomically creates one entry and appends later content`() {
        runBlocking {
            assertNull(repository.getEntry(date).successValue())

            val text = repository.createContentForDate(date, NewWorkContent.Text("补录接口联调")).successValue()
            val table = repository.createContentForDate(date, NewWorkContent.Table(tableContent())).successValue()

            assertEquals(text.entry.id, table.entry.id)
            assertEquals(1, database.workEntryDao().getAllIncludingDeleted().size)
            assertEquals(
                listOf(ContentBlock.Text::class, ContentBlock.Table::class),
                repository
                    .getEntry(date)
                    .successValue()!!
                    .blocks
                    .map { it::class },
            )
        }
    }

    @Test
    fun `invalid first image leaves no work entry or content block`() {
        runBlocking {
            val result =
                repository.createContentForDate(
                    date,
                    NewWorkContent.Image(
                        AttachmentDraft(
                            localPath = "",
                            mimeType = "image/jpeg",
                            fileSize = 128L,
                        ),
                    ),
                )

            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_ATTACHMENT),
                result.failureValue(),
            )
            assertFalse(database.workEntryDao().existsByDateIncludingDeleted(date))
            assertTrue(database.contentBlockDao().getAll().isEmpty())
        }
    }

    @Test
    fun `soft deleted date is restored when first new content is created`() {
        runBlocking {
            val original = repository.getOrCreateEntry(date).successValue()
            repository.softDeleteEntry(original.id).successValue()

            val created = repository.createContentForDate(date, NewWorkContent.Text("补录恢复")).successValue()

            assertEquals(original.id, created.entry.id)
            assertFalse(created.entry.isDeleted)
            assertEquals("补录恢复", (created.block as ContentBlock.Text).content)
            assertEquals(1, database.workEntryDao().getAllIncludingDeleted().size)
        }
    }

    @Test
    fun `first block insert failure rolls back newly created date entry`() {
        val collisionRepository =
            OfflineWorkEntryRepository(
                database = database,
                workEntryDao = database.workEntryDao(),
                contentBlockDao = database.contentBlockDao(),
                attachmentDao = database.attachmentDao(),
                tableContentCodec = KotlinxTableContentCodec(),
                idGenerator = SequenceIdGenerator(listOf("entry-one", "block", "entry-two", "block")),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
        runBlocking {
            collisionRepository
                .createContentForDate(date, NewWorkContent.Text("first"))
                .successValue()
            val secondDate = date.plusDays(1)

            assertEquals(
                DataError.Conflict,
                collisionRepository
                    .createContentForDate(secondDate, NewWorkContent.Text("second"))
                    .failureValue(),
            )
            assertFalse(database.workEntryDao().existsByDateIncludingDeleted(secondDate))
            assertEquals(1, database.workEntryDao().getAllIncludingDeleted().size)
        }
    }

    @Test
    fun `concurrent first content requests still reuse one date entry`() {
        runBlocking {
            val results =
                coroutineScope {
                    listOf(
                        async { repository.createContentForDate(date, NewWorkContent.Text("text")) },
                        async { repository.createContentForDate(date, NewWorkContent.Table(tableContent())) },
                    ).awaitAll()
                }

            assertTrue(results.all { it is com.worklogai.app.core.common.result.DataResult.Success })
            assertEquals(1, database.workEntryDao().getAllIncludingDeleted().size)
            assertEquals(
                2,
                repository
                    .getEntry(date)
                    .successValue()!!
                    .blocks
                    .size,
            )
        }
    }

    private fun tableContent(title: String? = "进度") =
        TableContent(
            title = title,
            columns = listOf(TableColumn(id = "module", name = "模块")),
            rows = listOf(TableRow(id = "login", cells = mapOf("module" to "登录"))),
        )
}
