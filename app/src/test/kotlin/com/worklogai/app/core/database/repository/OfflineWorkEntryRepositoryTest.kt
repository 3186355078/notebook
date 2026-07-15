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
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
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

    private fun tableContent(title: String? = "进度") =
        TableContent(
            title = title,
            columns = listOf(TableColumn(id = "module", name = "模块")),
            rows = listOf(TableRow(id = "login", cells = mapOf("module" to "登录"))),
        )
}
