package com.worklogai.app.core.backup

import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.createInMemoryDatabase
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupDataGatewayTest {
    private lateinit var database: WorkLogDatabase
    private lateinit var daos: BackupDatabaseDaos
    private lateinit var original: BackupDatabaseSnapshot

    @Before
    fun setUp() =
        runBlocking {
            database = createInMemoryDatabase()
            daos =
                BackupDatabaseDaos(
                    database.workEntryDao(),
                    database.contentBlockDao(),
                    database.attachmentDao(),
                    database.workSummaryBackupDao(),
                )
            original = snapshot("old", LocalDate.of(2026, 7, 1))
            assertTrue(gateway().replace(original).isSuccess)
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `real Room transaction completely replaces every backup table`() =
        runBlocking {
            val replacement = snapshot("new", LocalDate.of(2026, 7, 8))

            assertTrue(gateway().replace(replacement).isSuccess)

            assertEquals(replacement, gateway().snapshot().getOrThrow())
        }

    @Test
    fun `every injected transaction checkpoint rolls all backup tables back`() =
        runBlocking {
            RestoreDatabaseCheckpoint.entries.forEach { checkpoint ->
                assertTrue(gateway().replace(original).isSuccess)
                val result =
                    gateway(
                        RestoreDatabaseFailureInjector { reached ->
                            if (reached == checkpoint) error("injected")
                        },
                    ).replace(snapshot("new", LocalDate.of(2026, 7, 8)))

                assertTrue("Expected failure at $checkpoint", result.isFailure)
                assertEquals(original, gateway().snapshot().getOrThrow())
            }
        }

    @Test
    fun `foreign key and duplicate entry date failures preserve the old database`() =
        runBlocking {
            val replacement = snapshot("new", LocalDate.of(2026, 7, 8))
            val brokenForeignKey = replacement.copy(blocks = replacement.blocks.map { it.copy(entryId = "missing") })
            val duplicateDate =
                replacement.copy(
                    entries = replacement.entries + replacement.entries.single().copy(id = "duplicate-entry"),
                )

            assertTrue(gateway().replace(brokenForeignKey).isFailure)
            assertEquals(original, gateway().snapshot().getOrThrow())
            assertTrue(gateway().replace(duplicateDate).isFailure)
            assertEquals(original, gateway().snapshot().getOrThrow())
        }

    @Test
    fun `duplicate summary period preserves the old database`() =
        runBlocking {
            val replacement = snapshot("new", LocalDate.of(2026, 7, 8))
            val duplicatePeriod =
                replacement.copy(
                    summaries = replacement.summaries + replacement.summaries.single().copy(id = "duplicate-summary"),
                )

            assertTrue(gateway().replace(duplicatePeriod).isFailure)
            assertEquals(original, gateway().snapshot().getOrThrow())
        }

    @Test
    fun `cancellation inside the real Room transaction rolls back and remains cancellation`() =
        runBlocking {
            var cancelled = false
            try {
                gateway(
                    RestoreDatabaseFailureInjector { checkpoint ->
                        if (checkpoint == RestoreDatabaseCheckpoint.AFTER_INSERT_CONTENT_BLOCKS) {
                            throw CancellationException("injected")
                        }
                    },
                ).replace(snapshot("new", LocalDate.of(2026, 7, 8)))
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
            assertEquals(original, gateway().snapshot().getOrThrow())
        }

    private fun gateway(
        injector: RestoreDatabaseFailureInjector = NoOpRestoreDatabaseFailureInjector(),
    ): RoomBackupDataGateway = RoomBackupDataGateway(database, daos, injector, Dispatchers.Unconfined)

    private fun snapshot(
        prefix: String,
        date: LocalDate,
    ): BackupDatabaseSnapshot {
        val entryId = "$prefix-entry"
        val blockId = "$prefix-block"
        return BackupDatabaseSnapshot(
            entries = listOf(WorkEntryEntity(entryId, date, prefix, true, false, NOW, NOW)),
            blocks =
                listOf(
                    ContentBlockEntity(
                        blockId,
                        entryId,
                        ContentBlockType.IMAGE,
                        0,
                        null,
                        null,
                        NOW,
                        NOW,
                    ),
                ),
            attachments =
                listOf(
                    AttachmentEntity(
                        "$prefix-attachment",
                        blockId,
                        "images/$prefix.jpg",
                        "image/jpeg",
                        3,
                        1,
                        1,
                        prefix,
                        NOW,
                    ),
                ),
            summaries =
                listOf(
                    WorkSummaryEntity(
                        "$prefix-summary",
                        SummaryType.WEEKLY,
                        date,
                        date.plusDays(6),
                        SummaryStatus.SUCCESS,
                        null,
                        "mock",
                        "mock",
                        prefix,
                        null,
                        null,
                        NOW,
                        NOW,
                        NOW,
                    ),
                ),
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-15T00:00:00Z")
    }
}
