package com.worklogai.app.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.ArchiveReadResult
import com.worklogai.app.core.backup.BACKUP_FORMAT_VERSION
import com.worklogai.app.core.backup.BackupArchiveContract
import com.worklogai.app.core.backup.BackupArchiveReader
import com.worklogai.app.core.backup.BackupArchiveStreams
import com.worklogai.app.core.backup.BackupContentBlock
import com.worklogai.app.core.backup.BackupFileManifest
import com.worklogai.app.core.backup.BackupManifest
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.backup.BackupSafetyLimits
import com.worklogai.app.core.backup.BackupWorkEntry
import com.worklogai.app.core.backup.backupJson
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.model.ContentBlockType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DeviceBackupSafetyIntegrationTest {
    @Test
    fun devicePreviewRejectsManifestVersionShaSizeAndMissingCoreDataWithoutMutation() =
        runBlocking {
            withFixture { fixture ->
                val baseline = fixture.databaseCounts()
                val variants =
                    listOf(
                        fixture.rewriteEntry(BackupArchiveContract.MANIFEST_FILE, "{".encodeToByteArray()),
                        fixture.rewriteManifest { it.copy(formatName = "not-worklog") },
                        fixture.rewriteManifest { it.copy(formatVersion = BACKUP_FORMAT_VERSION + 1) },
                        fixture.rewriteEntry(
                            BackupArchiveContract.ENTRIES_FILE,
                            fixture.entryBytes(BackupArchiveContract.ENTRIES_FILE) + ' '.code.toByte(),
                        ),
                        fixture.rewriteManifest { manifest ->
                            manifest.copy(
                                files =
                                    manifest.files.mapIndexed { index, file ->
                                        if (index == 0) file.copy(size = file.size + 1) else file
                                    },
                            )
                        },
                        fixture.withoutEntry(BackupArchiveContract.BLOCKS_FILE),
                    )

                variants.forEach { bytes ->
                    assertFailure(fixture, bytes)
                    assertEquals(baseline, fixture.databaseCounts())
                }
            }
        }

    @Test
    fun devicePreviewRejectsDangerousDuplicateUndeclaredAndInvalidRelationshipArchives() =
        runBlocking {
            withFixture { fixture ->
                val baseline = fixture.databaseCounts()
                val entriesBytes = fixture.entryBytes(BackupArchiveContract.ENTRIES_FILE)
                val variants =
                    listOf(
                        fixture.withAdditionalEntry("./data/work_entries.json", entriesBytes),
                        fixture.withAdditionalEntry("data//work_entries.json", entriesBytes),
                        fixture.withAdditionalEntry("../evil", byteArrayOf(1)),
                        fixture.withAdditionalEntry("C:/evil", byteArrayOf(1)),
                        fixture.withAdditionalEntry("data/extra.json", "{}".encodeToByteArray()),
                        fixture.mutatePayload<BackupWorkEntry>(BackupArchiveContract.ENTRIES_FILE) { values ->
                            values + values.first().copy(id = "stage9-duplicate-date")
                        },
                        fixture.mutatePayload<BackupContentBlock>(BackupArchiveContract.BLOCKS_FILE) { values ->
                            values.mapIndexed { index, block ->
                                if (index == 0) block.copy(entryId = "missing-entry") else block
                            }
                        },
                        fixture.mutatePayload<BackupContentBlock>(BackupArchiveContract.BLOCKS_FILE) { values ->
                            values.mapIndexed { index, block ->
                                if (index == values.lastIndex) {
                                    block.copy(
                                        blockType = ContentBlockType.TABLE.name,
                                        textContent = null,
                                        structuredContent = "{",
                                    )
                                } else {
                                    block
                                }
                            }
                        },
                    )

                variants.forEach { bytes ->
                    assertFailure(fixture, bytes)
                    assertEquals(baseline, fixture.databaseCounts())
                }
            }
        }

    @Test
    fun deviceReaderEnforcesArchiveEntryCompressionAndPathLimitsBeforeRestore() =
        runBlocking {
            withFixture { fixture ->
                val cache =
                    fixture.context.cacheDir
                        .resolve("stage9-safety-limits")
                        .apply { mkdirs() }
                val readers =
                    listOf(
                        BackupArchiveReader(
                            KotlinxTableContentCodec(),
                            DefaultWorkPeriodCalculator(),
                            BackupSafetyLimits(maxArchiveSizeBytes = fixture.archive.size.toLong() - 1),
                        ) to fixture.archive,
                        BackupArchiveReader(
                            KotlinxTableContentCodec(),
                            DefaultWorkPeriodCalculator(),
                            BackupSafetyLimits(maxEntryCount = 1),
                        ) to fixture.archive,
                        BackupArchiveReader(
                            KotlinxTableContentCodec(),
                            DefaultWorkPeriodCalculator(),
                            BackupSafetyLimits(maxSingleEntrySizeBytes = 8),
                        ) to fixture.archive,
                        BackupArchiveReader(
                            KotlinxTableContentCodec(),
                            DefaultWorkPeriodCalculator(),
                            BackupSafetyLimits(maxCompressionRatio = 1),
                        ) to
                            fixture.withAdditionalEntry(
                                "attachments/images/compression.png",
                                ByteArray(COMPRESSIBLE_BYTES) { 0 },
                            ),
                        BackupArchiveReader(
                            KotlinxTableContentCodec(),
                            DefaultWorkPeriodCalculator(),
                            BackupSafetyLimits(maxPathLength = 12),
                        ) to fixture.archive,
                    )

                try {
                    readers.forEach { (reader, bytes) ->
                        assertTrue(reader.readArchive(ByteArrayInputStream(bytes), cache) is ArchiveReadResult.Failure)
                    }
                    assertEquals(fixture.baselineCounts, fixture.databaseCounts())
                } finally {
                    cache.deleteRecursively()
                }
            }
        }

    private suspend fun assertFailure(
        fixture: BackupFixture,
        bytes: ByteArray,
    ) {
        val result = fixture.service.inspectBackup(ByteArrayInputStream(bytes))
        assertTrue(result is BackupOperationResult.Failure)
        val message = (result as BackupOperationResult.Failure).message
        assertTrue(
            !message.contains("../") && !message.contains(":/") && !message.contains(fixture.context.filesDir.path),
        )
    }

    private suspend fun withFixture(block: suspend (BackupFixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
        val database = dependencies.database()
        val entryDao = database.workEntryDao()
        val existing = entryDao.getWithContentByDateIncludingDeleted(FIXTURE_DATE)?.entry
        existing?.let { entryDao.delete(it) }
        val now = Instant.parse("2026-07-01T00:00:00Z")
        val entry =
            WorkEntryEntity(
                id = FIXTURE_ENTRY_ID,
                entryDate = FIXTURE_DATE,
                title = "Stage 9 backup fixture",
                allowAiProcessing = true,
                isDeleted = false,
                createdAt = now,
                updatedAt = now,
            )
        val blocks =
            listOf(
                ContentBlockEntity(
                    id = "$FIXTURE_ENTRY_ID-text",
                    entryId = FIXTURE_ENTRY_ID,
                    blockType = ContentBlockType.TEXT,
                    blockOrder = 0,
                    textContent = "Synthetic backup safety record",
                    structuredContent = null,
                    createdAt = now,
                    updatedAt = now,
                ),
                ContentBlockEntity(
                    id = "$FIXTURE_ENTRY_ID-image",
                    entryId = FIXTURE_ENTRY_ID,
                    blockType = ContentBlockType.IMAGE,
                    blockOrder = 1,
                    textContent = null,
                    structuredContent = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val image = context.filesDir.resolve("attachments/images/stage9-fixture.png")
        image.parentFile?.mkdirs()
        image.writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG))
        val attachment =
            AttachmentEntity(
                id = "$FIXTURE_ENTRY_ID-attachment",
                blockId = "$FIXTURE_ENTRY_ID-image",
                localPath = "images/stage9-fixture.png",
                mimeType = "image/png",
                fileSize = image.length(),
                width = 1,
                height = 1,
                caption = "Synthetic image caption",
                createdAt = now,
            )
        entryDao.insert(entry)
        database.contentBlockDao().insertAll(blocks)
        database.attachmentDao().insert(attachment)
        val output = ByteArrayOutputStream()
        try {
            assertTrue(dependencies.backupArchiveService().createBackup(output) is BackupOperationResult.Success)
            val fixture = BackupFixture(context, dependencies.backupArchiveService(), database, output.toByteArray())
            block(fixture)
        } finally {
            entryDao.getByIdIncludingDeleted(FIXTURE_ENTRY_ID)?.let { entryDao.delete(it) }
            image.delete()
        }
    }

    private data class BackupFixture(
        val context: android.content.Context,
        val service: com.worklogai.app.core.backup.BackupArchiveService,
        val database: com.worklogai.app.core.database.WorkLogDatabase,
        val archive: ByteArray,
    ) {
        val baselineCounts = runBlocking { databaseCounts() }

        suspend fun databaseCounts(): DatabaseCounts =
            DatabaseCounts(
                database.workEntryDao().getAllIncludingDeleted().size,
                database.contentBlockDao().getAll().size,
                database.attachmentDao().getAll().size,
                database.workSummaryBackupDao().getAll().size,
            )

        fun entryBytes(name: String): ByteArray = readEntries().first { it.name == name }.bytes

        fun rewriteEntry(
            name: String,
            bytes: ByteArray,
        ): ByteArray = writeEntries(readEntries().map { if (it.name == name) it.copy(bytes = bytes) else it })

        fun withoutEntry(name: String): ByteArray = writeEntries(readEntries().filterNot { it.name == name })

        fun withAdditionalEntry(
            name: String,
            bytes: ByteArray,
        ): ByteArray = writeEntries(readEntries() + ArchiveEntry(name, bytes, false))

        fun rewriteManifest(transform: (BackupManifest) -> BackupManifest): ByteArray {
            val entries = readEntries()
            val manifestEntry = entries.first { it.name == BackupArchiveContract.MANIFEST_FILE }
            val manifest = backupJson.decodeFromString<BackupManifest>(manifestEntry.bytes.decodeToString())
            return writeEntries(
                entries.map {
                    if (it.name == BackupArchiveContract.MANIFEST_FILE) {
                        it.copy(bytes = backupJson.encodeToString(transform(manifest)).encodeToByteArray())
                    } else {
                        it
                    }
                },
            )
        }

        inline fun <reified T> mutatePayload(
            path: String,
            transform: (List<T>) -> List<T>,
        ): ByteArray {
            val entries = readEntries().toMutableList()
            val payloadIndex = entries.indexOfFirst { it.name == path }
            val original = backupJson.decodeFromString<List<T>>(entries[payloadIndex].bytes.decodeToString())
            val changed = backupJson.encodeToString(transform(original)).encodeToByteArray()
            entries[payloadIndex] = entries[payloadIndex].copy(bytes = changed)
            val manifestIndex = entries.indexOfFirst { it.name == BackupArchiveContract.MANIFEST_FILE }
            val manifest = backupJson.decodeFromString<BackupManifest>(entries[manifestIndex].bytes.decodeToString())
            val count = transform(original).size
            val changedManifest =
                manifest
                    .copy(
                        entryCount = if (path == BackupArchiveContract.ENTRIES_FILE) count else manifest.entryCount,
                        blockCount = if (path == BackupArchiveContract.BLOCKS_FILE) count else manifest.blockCount,
                        files =
                            manifest.files.map { file ->
                                if (file.path == path) {
                                    BackupFileManifest(
                                        path,
                                        changed.size.toLong(),
                                        BackupArchiveStreams.sha256(changed),
                                    )
                                } else {
                                    file
                                }
                            },
                    )
            entries[manifestIndex] =
                entries[manifestIndex].copy(bytes = backupJson.encodeToString(changedManifest).encodeToByteArray())
            return writeEntries(entries)
        }

        private fun readEntries(): List<ArchiveEntry> = readZipEntries(archive)
    }

    private data class DatabaseCounts(
        val entries: Int,
        val blocks: Int,
        val attachments: Int,
        val summaries: Int,
    )

    private data class ArchiveEntry(
        val name: String,
        val bytes: ByteArray,
        val directory: Boolean,
    )

    private companion object {
        val FIXTURE_DATE: LocalDate = LocalDate.of(1901, 1, 1)
        const val FIXTURE_ENTRY_ID = "stage9-backup-fixture"
        const val COMPRESSIBLE_BYTES = 16_384
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+Xxw0WQAAAABJRU5ErkJggg=="

        fun readZipEntries(bytes: ByteArray): List<ArchiveEntry> =
            buildList {
                ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        add(
                            ArchiveEntry(
                                entry.name,
                                if (entry.isDirectory) byteArrayOf() else input.readBytes(),
                                entry.isDirectory,
                            ),
                        )
                        input.closeEntry()
                    }
                }
            }

        fun writeEntries(entries: List<ArchiveEntry>): ByteArray =
            ByteArrayOutputStream().use { output ->
                ZipOutputStream(output).use { zip ->
                    entries.forEach { entry ->
                        zip.putNextEntry(
                            ZipEntry(
                                entry.name + if (entry.directory && !entry.name.endsWith('/')) "/" else "",
                            ),
                        )
                        if (!entry.directory) zip.write(entry.bytes)
                        zip.closeEntry()
                    }
                }
                output.toByteArray()
            }
    }
}
