package com.worklogai.app.core.database.dao

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.createInMemoryDatabase
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
class WorkLogDatabaseTest {
    private lateinit var database: WorkLogDatabase
    private val firstDate = LocalDate.of(2026, 7, 6)
    private val secondDate = LocalDate.of(2026, 7, 7)
    private val baseTime = Instant.parse("2026-07-06T08:00:00Z")

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `stores dates as an inclusive range and supports soft delete restore`() {
        runBlocking {
            database.workEntryDao().insert(entry(id = "entry-1", date = firstDate))
            database.workEntryDao().insert(entry(id = "entry-2", date = secondDate))

            val range = database.workEntryDao().getActiveWithContentInDateRange(firstDate, secondDate)
            assertEquals(listOf(firstDate, secondDate), range.map { it.entry.entryDate })

            assertEquals(1, database.workEntryDao().softDelete("entry-1", baseTime.plusSeconds(1)))
            assertNull(database.workEntryDao().getActiveWithContentByDate(firstDate))
            assertTrue(
                database
                    .workEntryDao()
                    .getWithContentByDateIncludingDeleted(firstDate)
                    ?.entry
                    ?.isDeleted == true,
            )

            assertEquals(1, database.workEntryDao().restore("entry-1", baseTime.plusSeconds(2)))
            assertFalse(
                database
                    .workEntryDao()
                    .getActiveWithContentByDate(firstDate)
                    ?.entry
                    ?.isDeleted ?: true,
            )
            assertEquals(WorkLogDatabase.VERSION, database.openHelper.writableDatabase.version)
        }
    }

    @Test
    fun `enforces entry date uniqueness stable block ordering and cascading deletes`() {
        runBlocking {
            database.workEntryDao().insert(entry(id = "entry-1", date = firstDate))

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { database.workEntryDao().insert(entry(id = "entry-duplicate", date = firstDate)) }
            }

            database.contentBlockDao().insert(
                block(id = "block-later", order = 0, createdAt = baseTime.plusSeconds(1)),
            )
            database.contentBlockDao().insert(
                block(id = "block-earlier", order = 0, createdAt = baseTime),
            )
            database.attachmentDao().insert(
                AttachmentEntity(
                    id = "attachment-1",
                    blockId = "block-earlier",
                    localPath = "attachments/one.jpg",
                    mimeType = "image/jpeg",
                    fileSize = 12,
                    width = 2,
                    height = 2,
                    caption = "截图",
                    createdAt = baseTime,
                ),
            )

            assertEquals(
                listOf("block-earlier", "block-later"),
                database.contentBlockDao().getByEntryId("entry-1").map(ContentBlockEntity::id),
            )
            assertEquals(
                listOf("attachments/one.jpg"),
                database.attachmentDao().getPathsByEntryId("entry-1"),
            )

            database.contentBlockDao().delete(database.contentBlockDao().getById("block-earlier")!!)
            assertNull(database.attachmentDao().getById("attachment-1"))

            database.workEntryDao().delete(database.workEntryDao().getByIdIncludingDeleted("entry-1")!!)
            assertTrue(database.contentBlockDao().getByEntryId("entry-1").isEmpty())

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    database.attachmentDao().insert(
                        AttachmentEntity(
                            id = "orphan",
                            blockId = "missing-block",
                            localPath = "attachments/missing.jpg",
                            mimeType = "image/jpeg",
                            fileSize = 1,
                            width = null,
                            height = null,
                            caption = null,
                            createdAt = baseTime,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `keeps weekly and monthly summaries separate and enforces a period unique index`() {
        runBlocking {
            val weekly = summary(id = "weekly-1", type = SummaryType.WEEKLY)
            database.workSummaryDao().insert(weekly)
            database.workSummaryDao().insert(summary(id = "monthly-1", type = SummaryType.MONTHLY))

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    database.workSummaryDao().insert(
                        summary(id = "weekly-duplicate", type = SummaryType.WEEKLY),
                    )
                }
            }

            assertEquals(
                1,
                database
                    .workSummaryDao()
                    .observeByType(SummaryType.WEEKLY)
                    .first()
                    .size,
            )
            assertEquals(
                1,
                database
                    .workSummaryDao()
                    .observeByType(SummaryType.MONTHLY)
                    .first()
                    .size,
            )
            assertEquals(
                1,
                database.workSummaryDao().updateStatus(
                    summaryType = SummaryType.WEEKLY,
                    periodStart = firstDate,
                    periodEnd = secondDate,
                    status = SummaryStatus.FAILED,
                    errorMessage = "网络暂时不可用",
                    updatedAt = baseTime.plusSeconds(2),
                ),
            )
            assertEquals(
                SummaryStatus.FAILED,
                database.workSummaryDao().getByPeriod(SummaryType.WEEKLY, firstDate, secondDate)?.status,
            )
        }
    }

    private fun entry(
        id: String,
        date: LocalDate,
    ): WorkEntryEntity =
        WorkEntryEntity(
            id = id,
            entryDate = date,
            title = null,
            allowAiProcessing = true,
            isDeleted = false,
            createdAt = baseTime,
            updatedAt = baseTime,
        )

    private fun block(
        id: String,
        order: Int,
        createdAt: Instant,
    ): ContentBlockEntity =
        ContentBlockEntity(
            id = id,
            entryId = "entry-1",
            blockType = ContentBlockType.IMAGE,
            blockOrder = order,
            textContent = null,
            structuredContent = null,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun summary(
        id: String,
        type: SummaryType,
    ): WorkSummaryEntity =
        WorkSummaryEntity(
            id = id,
            summaryType = type,
            periodStart = firstDate,
            periodEnd = secondDate,
            status = SummaryStatus.PENDING,
            sourceHash = null,
            aiProvider = null,
            modelName = null,
            originalContent = null,
            editedContent = null,
            errorMessage = null,
            createdAt = baseTime,
            updatedAt = baseTime,
            generatedAt = null,
        )
}
