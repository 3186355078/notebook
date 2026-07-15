package com.worklogai.app.core.database.repository

import android.app.Application
import com.worklogai.app.core.database.FixedTimeProvider
import com.worklogai.app.core.database.SequenceIdGenerator
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.createInMemoryDatabase
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.successValue
import com.worklogai.app.core.model.AttachmentDraft
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class OfflineWorkHistoryRepositoryTest {
    private lateinit var database: com.worklogai.app.core.database.WorkLogDatabase
    private lateinit var entryRepository: OfflineWorkEntryRepository
    private lateinit var historyRepository: OfflineWorkHistoryRepository
    private val codec = KotlinxTableContentCodec()

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        entryRepository =
            OfflineWorkEntryRepository(
                database = database,
                workEntryDao = database.workEntryDao(),
                contentBlockDao = database.contentBlockDao(),
                attachmentDao = database.attachmentDao(),
                tableContentCodec = codec,
                idGenerator = SequenceIdGenerator((1..200).map { "id-$it" }),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                ioDispatcher = Dispatchers.Unconfined,
            )
        historyRepository =
            OfflineWorkHistoryRepository(
                workEntryDao = database.workEntryDao(),
                tableContentCodec = codec,
                ioDispatcher = Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `recent history excludes empty and deleted entries while retaining image and table entries`() =
        runBlocking {
            createEntry(LocalDate.of(2026, 7, 1))
            createTextEntry(LocalDate.of(2026, 7, 2), "   \n")
            createImageEntry(LocalDate.of(2026, 7, 3), "\u8054\u8c03\u622a\u56fe")
            createTableEntry(LocalDate.of(2026, 7, 4), "\u6d4b\u8bd5\u7ed3\u679c", "\u901a\u8fc7")
            val deleted = createTextEntry(LocalDate.of(2026, 7, 5), "\u5df2\u5220\u9664")
            entryRepository.softDeleteEntry(deleted.id).successValue()
            createTextEntry(LocalDate.of(2026, 7, 6), "\u6700\u65b0\u8bb0\u5f55")

            val summaries = historyRepository.getRecentEntries(30, 0).successValue().entries

            assertEquals(
                listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 3)),
                summaries.map { it.date },
            )
            assertEquals("\u56fe\u7247\u8bb0\u5f55", summaries.last().previewText)
            assertEquals("\u6d4b\u8bd5\u7ed3\u679c", summaries[1].previewText)
        }

    @Test
    fun `recent pages are date descending and do not duplicate entries`() =
        runBlocking {
            (1..5).forEach { day -> createTextEntry(LocalDate.of(2026, 7, day), "\u8bb0\u5f55$day") }

            val first = historyRepository.getRecentEntries(2, 0).successValue()
            val second = historyRepository.getRecentEntries(2, first.nextOffset!!).successValue()
            val dates = first.entries.map { it.date } + second.entries.map { it.date }

            assertEquals(
                listOf(
                    LocalDate.of(2026, 7, 5),
                    LocalDate.of(2026, 7, 4),
                    LocalDate.of(2026, 7, 3),
                    LocalDate.of(2026, 7, 2),
                ),
                dates,
            )
            assertEquals(dates.size, dates.distinct().size)
        }

    @Test
    fun `date range includes both endpoints and orders results descending`() =
        runBlocking {
            createTextEntry(LocalDate.of(2026, 7, 5), "\u8d77\u59cb")
            createTextEntry(LocalDate.of(2026, 7, 6), "\u4e2d\u95f4")
            createTextEntry(LocalDate.of(2026, 7, 7), "\u7ed3\u675f")

            val entries =
                historyRepository
                    .getEntriesInRange(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 7))
                    .successValue()

            assertEquals(
                listOf(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 5)),
                entries.map { it.date },
            )
        }

    @Test
    fun `searches title text image caption and structured table values without paths`() =
        runBlocking {
            val titleEntry = createEntry(LocalDate.of(2026, 7, 1))
            entryRepository.updateEntryTitle(titleEntry.id, "Release Checklist").successValue()
            createTextEntry(LocalDate.of(2026, 7, 2), "\u5b8c\u6210\u767b\u5f55\u8054\u8c03")
            createImageEntry(LocalDate.of(2026, 7, 3), "\u4ea7\u54c1\u8bc4\u5ba1\u622a\u56fe")
            createTableEntry(LocalDate.of(2026, 7, 4), "\u6d4b\u8bd5\u7ed3\u679c", "Regression Passed")

            assertEquals(1, search("checklist").size)
            assertEquals(1, search("\u767b\u5f55").size)
            assertEquals(1, search("\u8bc4\u5ba1").size)
            assertEquals(1, search("regression").size)
            assertTrue(search("images/imported.jpg").isEmpty())
        }

    @Test
    fun `invalid table JSON does not fail recent list or unrelated searches`() =
        runBlocking {
            val entry = createEntry(LocalDate.of(2026, 7, 10))
            database
                .contentBlockDao()
                .insert(
                    ContentBlockEntity(
                        id = "invalid-table",
                        entryId = entry.id,
                        blockType = ContentBlockType.TABLE,
                        blockOrder = 0,
                        textContent = null,
                        structuredContent = "{broken",
                        createdAt = Instant.parse("2026-07-12T08:00:00Z"),
                        updatedAt = Instant.parse("2026-07-12T08:00:00Z"),
                    ),
                )
            createTextEntry(LocalDate.of(2026, 7, 11), "\u53ef\u641c\u7d22\u8bb0\u5f55")

            val recent = historyRepository.getRecentEntries(30, 0).successValue()
            val search = historyRepository.searchEntries("\u53ef\u641c\u7d22", 30, 0).successValue()

            assertTrue(recent.entries.any { it.entryId == entry.id })
            assertEquals(1, search.entries.size)
            assertFalse(search.entries.any { it.entryId == entry.id })
        }

    private suspend fun search(query: String) = historyRepository.searchEntries(query, 30, 0).successValue().entries

    private suspend fun createEntry(date: LocalDate) = entryRepository.getOrCreateEntry(date).successValue()

    private suspend fun createTextEntry(
        date: LocalDate,
        text: String,
    ) = createEntry(date).also { entry -> entryRepository.addTextBlock(entry.id, text).successValue() }

    private suspend fun createImageEntry(
        date: LocalDate,
        caption: String,
    ) = createEntry(date).also { entry ->
        entryRepository
            .addImageBlock(
                entry.id,
                AttachmentDraft("images/imported.jpg", "image/jpeg", 12, caption = caption),
            ).successValue()
    }

    private suspend fun createTableEntry(
        date: LocalDate,
        title: String,
        cell: String,
    ) = createEntry(date).also { entry ->
        entryRepository
            .addTableBlock(
                entry.id,
                TableContent(
                    title = title,
                    columns = listOf(TableColumn("status", "\u72b6\u6001")),
                    rows = listOf(TableRow("row", mapOf("status" to cell))),
                ),
            ).successValue()
    }
}
