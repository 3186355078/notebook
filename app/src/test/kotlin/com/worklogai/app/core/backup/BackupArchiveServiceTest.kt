package com.worklogai.app.core.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.worklogai.app.core.attachment.AttachmentFileStore
import com.worklogai.app.core.attachment.CleanupResult
import com.worklogai.app.core.attachment.StoredImage
import com.worklogai.app.core.autosummary.AutoSummaryPeriod
import com.worklogai.app.core.autosummary.AutoSummaryScheduleState
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@Suppress("LargeClass")
class BackupArchiveServiceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val attachmentRoot = File(context.filesDir, "attachments")

    @After
    fun cleanUp() {
        attachmentRoot.deleteRecursively()
        File(context.cacheDir, "worklog-backup").deleteRecursively()
    }

    @Test
    fun `backup contains manifest data attachment and no secret`() {
        runBlocking {
            val gateway = FakeGateway(snapshot())
            val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val service = service(gateway, store)
            val output = ByteArrayOutputStream()

            val created = service.createBackup(output)
            val preview = service.inspectBackup(ByteArrayInputStream(output.toByteArray()))

            assertTrue(created is BackupOperationResult.Success)
            val value = (preview as BackupOperationResult.Success).value
            assertEquals(1, value.entryCount)
            assertEquals(1, value.todoCount)
            assertFalse(output.toString().contains("super-secret"))
        }
    }

    @Test
    fun `version one backup remains restorable and replaces todos with an empty list`() {
        runBlocking {
            val versionTwo = createArchive()
            val versionOne =
                rewriteArchive(versionTwo) { entries ->
                    val manifest =
                        backupJson.decodeFromString<BackupManifest>(
                            entries.getValue(BackupArchiveContract.MANIFEST_FILE).decodeToString(),
                        )
                    val v1Manifest =
                        manifest.copy(
                            formatVersion = 1,
                            databaseVersion = 1,
                            todoCount = 0,
                            files = manifest.files.filterNot { it.path == BackupArchiveContract.TODOS_FILE },
                        )
                    val legacyManifestJson =
                        JsonObject(
                            backupJson
                                .encodeToJsonElement(v1Manifest)
                                .jsonObject
                                .filterKeys { it != "todoCount" },
                        ).toString()
                    entries
                        .filterKeys { it != BackupArchiveContract.TODOS_FILE }
                        .plus(
                            BackupArchiveContract.MANIFEST_FILE to
                                legacyManifestJson.encodeToByteArray(),
                        )
                }
            val target = FakeGateway(snapshot(entryId = "current"))
            val store = TestAttachmentFileStore(attachmentRoot)

            val preview = service(target, store).inspectBackup(ByteArrayInputStream(versionOne))
            val restored = service(target, store).restoreBackup(ByteArrayInputStream(versionOne))

            assertEquals(0, (preview as BackupOperationResult.Success).value.todoCount)
            assertTrue(restored is BackupOperationResult.Success)
            assertTrue(target.current.todos.isEmpty())
            assertEquals(
                "entry",
                target.current.entries
                    .single()
                    .id,
            )
        }
    }

    @Test
    fun `version two validates todo count fields relations and safety limit`() {
        runBlocking {
            val archive = createArchive()
            val countMismatch = rewriteManifest(archive) { it.copy(todoCount = it.todoCount + 1) }
            val missingTodoCount =
                rewriteArchive(archive) { entries ->
                    val manifest =
                        backupJson
                            .parseToJsonElement(
                                entries
                                    .getValue(BackupArchiveContract.MANIFEST_FILE)
                                    .decodeToString(),
                            ).jsonObject
                    entries +
                        (
                            BackupArchiveContract.MANIFEST_FILE to
                                JsonObject(manifest.filterKeys { it != "todoCount" })
                                    .toString()
                                    .encodeToByteArray()
                        )
                }
            val invalidLink =
                rewritePayload(archive) { payload ->
                    payload.copy(
                        todos = payload.todos.map { it.copy(linkedContentBlockId = "missing-block") },
                    )
                }
            val invalidBlockTypeLink =
                rewritePayload(archive) { payload ->
                    payload.copy(
                        todos = payload.todos.map { it.copy(linkedContentBlockId = "image") },
                    )
                }
            val negativeOrder =
                rewritePayload(archive) { payload ->
                    payload.copy(todos = payload.todos.map { it.copy(sortOrder = -1) })
                }
            val recordLimited =
                BackupArchiveReader(
                    KotlinxTableContentCodec(),
                    DefaultWorkPeriodCalculator(),
                    BackupSafetyLimits(maxTodos = 0),
                )

            assertTrue(inspect(countMismatch) is BackupOperationResult.Failure)
            assertTrue(inspect(missingTodoCount) is BackupOperationResult.Failure)
            assertTrue(inspect(invalidLink) is BackupOperationResult.Failure)
            assertTrue(inspect(invalidBlockTypeLink) is BackupOperationResult.Failure)
            assertTrue(inspect(negativeOrder) is BackupOperationResult.Failure)
            assertTrue(isReadFailure(recordLimited, archive))
        }
    }

    @Test
    fun `version two rejects malformed duplicate and inconsistent todo records`() {
        runBlocking {
            val archive = createArchive()
            val invalidArchives =
                listOf(
                    rewritePayload(archive) { payload ->
                        payload.copy(todos = payload.todos + payload.todos.single())
                    },
                    rewritePayload(archive) { payload ->
                        payload.copy(todos = payload.todos.map { it.copy(scheduledDate = "not-a-date") })
                    },
                    rewritePayload(archive) { payload ->
                        payload.copy(todos = payload.todos.map { it.copy(priority = "TOP") })
                    },
                    rewritePayload(archive) { payload ->
                        payload.copy(todos = payload.todos.map { it.copy(status = "WAITING") })
                    },
                    rewritePayload(archive) { payload ->
                        payload.copy(todos = payload.todos.map { it.copy(title = "x".repeat(201)) })
                    },
                    rewritePayload(archive) { payload ->
                        payload.copy(todos = payload.todos.map { it.copy(completedAt = null) })
                    },
                    rewritePayload(archive) { payload ->
                        payload.copy(
                            todos =
                                payload.todos.map {
                                    it.copy(
                                        status = TodoStatus.NOT_STARTED.name,
                                        completedAt = NOW.toString(),
                                    )
                                },
                        )
                    },
                )

            invalidArchives.forEach { invalid ->
                assertTrue(inspect(invalid) is BackupOperationResult.Failure)
            }
        }
    }

    @Test
    fun `restore replaces database and attachment directory`() {
        runBlocking {
            val sourceStore =
                TestAttachmentFileStore(
                    attachmentRoot,
                ).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val source = FakeGateway(snapshot(entryId = "backup-entry"))
            val service = service(source, sourceStore)
            val archive = ByteArrayOutputStream()
            assertTrue(service.createBackup(archive) is BackupOperationResult.Success)

            sourceStore.write("images/old.jpg", byteArrayOf(9))
            val target = FakeGateway(snapshot(entryId = "old-entry"))
            val restored = service(target, sourceStore).restoreBackup(ByteArrayInputStream(archive.toByteArray()))

            assertTrue(restored is BackupOperationResult.Success)
            assertEquals(
                "backup-entry",
                target.current.entries
                    .single()
                    .id,
            )
            assertTrue(sourceStore.fileFor("images/picture.jpg")!!.isFile)
            assertFalse(sourceStore.fileFor("images/old.jpg")!!.exists())
            val restoredTodo = target.current.todos.single()
            assertEquals(TodoPriority.HIGH, restoredTodo.priority)
            assertEquals(TodoStatus.DONE, restoredTodo.status)
            assertEquals("todo-record", restoredTodo.linkedContentBlockId)
        }
    }

    @Test
    fun `malicious zip path is rejected before restoring current data`() {
        runBlocking {
            val gateway = FakeGateway(snapshot(entryId = "current"))
            val bytes =
                ByteArrayOutputStream().use { output ->
                    ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(ZipEntry("../outside.txt"))
                        zip.write(byteArrayOf(1))
                        zip.closeEntry()
                    }
                    output.toByteArray()
                }

            val result =
                service(
                    gateway,
                    TestAttachmentFileStore(attachmentRoot),
                ).restoreBackup(ByteArrayInputStream(bytes))

            assertTrue(result is BackupOperationResult.Failure)
            assertEquals(
                "current",
                gateway.current.entries
                    .single()
                    .id,
            )
        }
    }

    @Test
    fun `database restore failure leaves original attachment directory intact`() {
        runBlocking {
            val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val archive = ByteArrayOutputStream()
            assertTrue(
                service(
                    FakeGateway(snapshot(entryId = "backup")),
                    store,
                ).createBackup(archive) is BackupOperationResult.Success,
            )
            store.write("images/original.jpg", byteArrayOf(8))
            val target = FakeGateway(snapshot(entryId = "original"), failReplace = true)

            val result = service(target, store).restoreBackup(ByteArrayInputStream(archive.toByteArray()))

            assertTrue(result is BackupOperationResult.Failure)
            assertEquals(
                "original",
                target.current.entries
                    .single()
                    .id,
            )
            assertTrue(store.fileFor("images/original.jpg")!!.isFile)
        }
    }

    @Test
    fun `backup accepts uppercase manifest sha but rejects changed declared data`() {
        runBlocking {
            val archive = createArchive()
            val uppercaseSha =
                rewriteArchive(archive) { entries ->
                    val manifest =
                        backupJson.decodeFromString<BackupManifest>(
                            entries.getValue("manifest.json").decodeToString(),
                        )
                    entries +
                        (
                            "manifest.json" to
                                backupJson
                                    .encodeToString(
                                        manifest.copy(
                                            files = manifest.files.map { it.copy(sha256 = it.sha256.uppercase()) },
                                        ),
                                    ).encodeToByteArray()
                        )
                }
            assertTrue(inspect(uppercaseSha) is BackupOperationResult.Success)

            val changedData =
                rewriteArchive(archive) { entries ->
                    entries + (BackupArchiveContract.ENTRIES_FILE to "[]".encodeToByteArray())
                }
            assertTrue(inspect(changedData) is BackupOperationResult.Failure)
        }
    }

    @Test
    fun `backup rejects manifest size conflict duplicate manifest path and undeclared file`() {
        runBlocking {
            val archive = createArchive()
            val sizeConflict =
                rewriteManifest(archive) { manifest ->
                    manifest.copy(
                        files =
                            manifest.files.mapIndexed { index, file ->
                                if (index ==
                                    0
                                ) {
                                    file.copy(size = file.size + 1)
                                } else {
                                    file
                                }
                            },
                    )
                }
            val duplicateManifestPath =
                rewriteManifest(archive) { manifest ->
                    manifest.copy(files = manifest.files + manifest.files.first())
                }
            val undeclaredFile =
                rewriteArchive(archive) { entries ->
                    entries + ("attachments/images/not-declared.jpg" to JPEG_HEADER)
                }

            assertTrue(inspect(sizeConflict) is BackupOperationResult.Failure)
            assertTrue(inspect(duplicateManifestPath) is BackupOperationResult.Failure)
            assertTrue(inspect(undeclaredFile) is BackupOperationResult.Failure)
        }
    }

    @Test
    fun `reader rejects archives that exceed injected entry count and compression ratio limits`() {
        runBlocking {
            val archive = createArchive()
            val limits = BackupSafetyLimits(maxEntryCount = 1)
            val entryLimited = BackupArchiveReader(KotlinxTableContentCodec(), DefaultWorkPeriodCalculator(), limits)
            val ratioLimited =
                BackupArchiveReader(
                    KotlinxTableContentCodec(),
                    DefaultWorkPeriodCalculator(),
                    BackupSafetyLimits(maxCompressionRatio = 1),
                )

            assertTrue(
                entryLimited.readArchive(ByteArrayInputStream(archive), context.cacheDir) is ArchiveReadResult.Failure,
            )
            assertTrue(
                ratioLimited.readArchive(ByteArrayInputStream(archive), context.cacheDir) is ArchiveReadResult.Failure,
            )
        }
    }

    @Test
    fun `reader applies injected json and payload record limits before restore staging`() {
        runBlocking {
            val archive = createArchive()
            val jsonLimited =
                BackupArchiveReader(
                    KotlinxTableContentCodec(),
                    DefaultWorkPeriodCalculator(),
                    BackupSafetyLimits(maxJsonSizeBytes = 1),
                )
            val recordLimits =
                listOf(
                    BackupSafetyLimits(maxEntries = 0),
                    BackupSafetyLimits(maxBlocks = 0),
                    BackupSafetyLimits(maxAttachments = 0),
                    BackupSafetyLimits(maxSummaries = 0),
                )

            assertTrue(isReadFailure(jsonLimited, archive))
            recordLimits.forEach { limits ->
                val reader = BackupArchiveReader(KotlinxTableContentCodec(), DefaultWorkPeriodCalculator(), limits)
                assertTrue(isReadFailure(reader, archive))
            }
        }
    }

    @Test
    fun `reader rejects duplicate dates broken block relations duplicate order and duplicate summary period`() {
        runBlocking {
            val archive = createArchive()
            val duplicateDate =
                rewritePayload(archive) { payload ->
                    payload.copy(entries = payload.entries + payload.entries.single().copy(id = "other-entry"))
                }
            val missingBlockEntry =
                rewritePayload(archive) { payload ->
                    payload.copy(blocks = payload.blocks.map { it.copy(entryId = "missing-entry") })
                }
            val duplicateOrder =
                rewritePayload(archive) { payload ->
                    payload.copy(
                        blocks =
                            payload.blocks +
                                payload.blocks.first().copy(
                                    id = "other-block",
                                    blockType = ContentBlockType.TEXT.name,
                                    textContent = "text",
                                ),
                    )
                }
            val duplicateSummaryPeriod =
                rewritePayload(archive) { payload ->
                    payload.copy(summaries = payload.summaries + payload.summaries.single().copy(id = "other-summary"))
                }

            assertTrue(inspect(duplicateDate) is BackupOperationResult.Failure)
            assertTrue(inspect(missingBlockEntry) is BackupOperationResult.Failure)
            assertTrue(inspect(duplicateOrder) is BackupOperationResult.Failure)
            assertTrue(inspect(duplicateSummaryPeriod) is BackupOperationResult.Failure)
        }
    }

    @Test
    fun `reader rejects invalid table attachment path and incomplete successful summary`() {
        runBlocking {
            val archive = createArchive()
            val invalidTable =
                rewritePayload(archive) { payload ->
                    payload.copy(
                        blocks =
                            payload.blocks +
                                BackupContentBlock(
                                    id = "table",
                                    entryId = payload.entries.single().id,
                                    blockType = ContentBlockType.TABLE.name,
                                    blockOrder = 1,
                                    structuredContent = "{",
                                    createdAt = NOW.toString(),
                                    updatedAt = NOW.toString(),
                                ),
                    )
                }
            val invalidAttachmentPath =
                rewritePayload(archive) { payload ->
                    payload.copy(attachments = payload.attachments.map { it.copy(localPath = "images/../outside.jpg") })
                }
            val emptySuccessSummary =
                rewritePayload(archive) { payload ->
                    payload.copy(
                        summaries =
                            payload.summaries.map {
                                it.copy(originalContent = null, editedContent = null)
                            },
                    )
                }

            assertTrue(inspect(invalidTable) is BackupOperationResult.Failure)
            assertTrue(inspect(invalidAttachmentPath) is BackupOperationResult.Failure)
            assertTrue(inspect(emptySuccessSummary) is BackupOperationResult.Failure)
        }
    }

    @Test
    fun `attachment directory switch failure keeps current database and image`() {
        runBlocking {
            val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val archive = ByteArrayOutputStream()
            assertTrue(
                service(
                    FakeGateway(snapshot(entryId = "backup")),
                    store,
                ).createBackup(archive) is BackupOperationResult.Success,
            )
            store.write("images/original.jpg", byteArrayOf(8))
            val target = FakeGateway(snapshot(entryId = "original"))

            val result =
                service(
                    target,
                    store,
                    directoryOperations = TestRestoreDirectoryOperations(failOnMove = STAGING_MOVE),
                ).restoreBackup(ByteArrayInputStream(archive.toByteArray()))

            assertTrue(result is BackupOperationResult.Failure)
            assertFalse((result as BackupOperationResult.Failure).requiresRecovery)
            assertEquals(
                "original",
                target.current.entries
                    .single()
                    .id,
            )
            assertTrue(store.fileFor("images/original.jpg")!!.isFile)
        }
    }

    @Test
    fun `rollback failure reports recovery required rather than claiming original data is intact`() {
        runBlocking {
            val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val archive = ByteArrayOutputStream()
            assertTrue(
                service(
                    FakeGateway(snapshot(entryId = "backup")),
                    store,
                ).createBackup(archive) is BackupOperationResult.Success,
            )
            store.write("images/original.jpg", byteArrayOf(8))

            val result =
                service(
                    FakeGateway(snapshot(entryId = "original"), failReplace = true),
                    store,
                    directoryOperations = TestRestoreDirectoryOperations(failOnMove = ROLLBACK_OLD_MOVE),
                ).restoreBackup(ByteArrayInputStream(archive.toByteArray()))

            assertTrue(result is BackupOperationResult.Failure)
            assertTrue((result as BackupOperationResult.Failure).requiresRecovery)
        }
    }

    @Test
    fun `scheduler failure keeps restored data and returns a warning`() {
        runBlocking {
            val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val archive = ByteArrayOutputStream()
            assertTrue(
                service(
                    FakeGateway(snapshot(entryId = "backup")),
                    store,
                ).createBackup(archive) is BackupOperationResult.Success,
            )
            val target = FakeGateway(snapshot(entryId = "original"))

            val result =
                service(
                    target,
                    store,
                    scheduler = FakeScheduler(failApply = true),
                ).restoreBackup(ByteArrayInputStream(archive.toByteArray()))

            assertTrue(result is BackupOperationResult.Success)
            assertEquals(
                "backup",
                target.current.entries
                    .single()
                    .id,
            )
            assertTrue((result as BackupOperationResult.Success).warningMessage != null)
        }
    }

    @Test
    fun `settings restore failure rolls back database and attachments`() {
        runBlocking {
            val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val archive = ByteArrayOutputStream()
            assertTrue(
                service(
                    FakeGateway(snapshot(entryId = "backup")),
                    store,
                ).createBackup(archive) is BackupOperationResult.Success,
            )
            store.write("images/original.jpg", byteArrayOf(8))
            val target = FakeGateway(snapshot(entryId = "original"))

            val result =
                service(
                    target,
                    store,
                    settings = FakeSettingsRepository(failNextSave = true),
                ).restoreBackup(ByteArrayInputStream(archive.toByteArray()))

            assertTrue(result is BackupOperationResult.Failure)
            assertFalse((result as BackupOperationResult.Failure).requiresRecovery)
            assertEquals(
                "original",
                target.current.entries
                    .single()
                    .id,
            )
            assertTrue(store.fileFor("images/original.jpg")!!.isFile)
        }
    }

    @Test
    fun `cancellation during database replacement rolls back switched attachments`() {
        runBlocking {
            val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
            val archive = ByteArrayOutputStream()
            assertTrue(
                service(
                    FakeGateway(snapshot(entryId = "backup")),
                    store,
                ).createBackup(archive) is BackupOperationResult.Success,
            )
            store.write("images/original.jpg", byteArrayOf(8))
            val target = FakeGateway(snapshot(entryId = "original"), cancelReplace = true)
            var cancelled = false

            try {
                service(target, store).restoreBackup(ByteArrayInputStream(archive.toByteArray()))
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
            assertEquals(
                "original",
                target.current.entries
                    .single()
                    .id,
            )
            assertTrue(store.fileFor("images/original.jpg")!!.isFile)
        }
    }

    private fun service(
        gateway: FakeGateway,
        store: TestAttachmentFileStore,
        scheduler: FakeScheduler = FakeScheduler(),
        directoryOperations: RestoreDirectoryOperations = DefaultRestoreDirectoryOperations(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): DefaultBackupArchiveService {
        val periods = DefaultWorkPeriodCalculator()
        return DefaultBackupArchiveService(
            context,
            BackupArchiveWriter(context, gateway, store, settings, TimeProvider { NOW }),
            BackupArchiveReader(KotlinxTableContentCodec(), periods),
            BackupRestoreCoordinator(
                context,
                gateway,
                store,
                RestoreDependencies(
                    settings,
                    scheduler,
                    FakeScheduleStateRepository(),
                    directoryOperations,
                ),
            ),
            Dispatchers.Unconfined,
        )
    }

    private suspend fun createArchive(): ByteArray {
        val store = TestAttachmentFileStore(attachmentRoot).also { it.write("images/picture.jpg", JPEG_HEADER) }
        val output = ByteArrayOutputStream()
        assertTrue(service(FakeGateway(snapshot()), store).createBackup(output) is BackupOperationResult.Success)
        return output.toByteArray()
    }

    private suspend fun inspect(archive: ByteArray): BackupOperationResult<BackupPreview> =
        service(
            FakeGateway(snapshot()),
            TestAttachmentFileStore(attachmentRoot),
        ).inspectBackup(ByteArrayInputStream(archive))

    private fun isReadFailure(
        reader: BackupArchiveReader,
        archive: ByteArray,
    ): Boolean = reader.readArchive(ByteArrayInputStream(archive), context.cacheDir) is ArchiveReadResult.Failure

    private fun rewriteManifest(
        archive: ByteArray,
        transform: (BackupManifest) -> BackupManifest,
    ): ByteArray =
        rewriteArchive(archive) { entries ->
            val manifest =
                backupJson.decodeFromString<BackupManifest>(
                    entries.getValue("manifest.json").decodeToString(),
                )
            entries + ("manifest.json" to backupJson.encodeToString(transform(manifest)).encodeToByteArray())
        }

    private fun rewritePayload(
        archive: ByteArray,
        transform: (BackupPayload) -> BackupPayload,
    ): ByteArray =
        rewriteArchive(archive) { entries ->
            val manifest =
                backupJson.decodeFromString<BackupManifest>(
                    entries.getValue("manifest.json").decodeToString(),
                )
            val payload =
                BackupPayload(
                    backupJson.decodeFromString(entries.getValue(BackupArchiveContract.ENTRIES_FILE).decodeToString()),
                    backupJson.decodeFromString(entries.getValue(BackupArchiveContract.BLOCKS_FILE).decodeToString()),
                    backupJson.decodeFromString(
                        entries.getValue(BackupArchiveContract.ATTACHMENTS_FILE).decodeToString(),
                    ),
                    backupJson.decodeFromString(
                        entries.getValue(BackupArchiveContract.SUMMARIES_FILE).decodeToString(),
                    ),
                    backupJson.decodeFromString(entries.getValue(BackupArchiveContract.SETTINGS_FILE).decodeToString()),
                    backupJson.decodeFromString(entries.getValue(BackupArchiveContract.TODOS_FILE).decodeToString()),
                )
            val changed = transform(payload)
            val replacementFiles =
                mapOf(
                    BackupArchiveContract.ENTRIES_FILE to
                        backupJson
                            .encodeToString(
                                changed.entries,
                            ).encodeToByteArray(),
                    BackupArchiveContract.BLOCKS_FILE to
                        backupJson
                            .encodeToString(
                                changed.blocks,
                            ).encodeToByteArray(),
                    BackupArchiveContract.ATTACHMENTS_FILE to
                        backupJson.encodeToString(changed.attachments).encodeToByteArray(),
                    BackupArchiveContract.SUMMARIES_FILE to
                        backupJson.encodeToString(changed.summaries).encodeToByteArray(),
                    BackupArchiveContract.SETTINGS_FILE to
                        backupJson.encodeToString(changed.settings).encodeToByteArray(),
                    BackupArchiveContract.TODOS_FILE to
                        backupJson.encodeToString(changed.todos).encodeToByteArray(),
                )
            val changedManifest =
                manifest.copy(
                    entryCount = changed.entries.size,
                    blockCount = changed.blocks.size,
                    attachmentCount = changed.attachments.size,
                    summaryCount = changed.summaries.size,
                    todoCount = changed.todos.size,
                    files =
                        manifest.files.map { file ->
                            replacementFiles[file.path]?.let { bytes ->
                                file.copy(size = bytes.size.toLong(), sha256 = BackupArchiveStreams.sha256(bytes))
                            } ?: file
                        },
                )
            entries + replacementFiles +
                ("manifest.json" to backupJson.encodeToString(changedManifest).encodeToByteArray())
        }

    private fun rewriteArchive(
        archive: ByteArray,
        transform: (Map<String, ByteArray>) -> Map<String, ByteArray>,
    ): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                transform(entries).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private fun snapshot(entryId: String = "entry") =
        BackupDatabaseSnapshot(
            entries = listOf(WorkEntryEntity(entryId, DATE, "Title", true, false, NOW, NOW)),
            blocks =
                listOf(
                    ContentBlockEntity("image", entryId, ContentBlockType.IMAGE, 0, null, null, NOW, NOW),
                    ContentBlockEntity(
                        "todo-record",
                        entryId,
                        ContentBlockType.TEXT,
                        1,
                        "已完成：完成备份验证",
                        null,
                        NOW,
                        NOW,
                    ),
                ),
            attachments =
                listOf(
                    AttachmentEntity(
                        "attachment",
                        "image",
                        "images/picture.jpg",
                        "image/jpeg",
                        3,
                        1,
                        1,
                        "caption",
                        NOW,
                    ),
                ),
            summaries =
                listOf(
                    WorkSummaryEntity(
                        "summary",
                        SummaryType.WEEKLY,
                        DATE.minusDays(1),
                        DATE.plusDays(5),
                        SummaryStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null,
                        "edited",
                        null,
                        NOW,
                        NOW,
                        NOW,
                    ),
                ),
            todos =
                listOf(
                    TodoEntity(
                        id = "todo",
                        scheduledDate = DATE,
                        title = "完成备份验证",
                        note = "模拟数据",
                        priority = TodoPriority.HIGH,
                        status = TodoStatus.DONE,
                        sortOrder = 0,
                        completionNote = "已通过",
                        linkedContentBlockId = "todo-record",
                        createdAt = NOW,
                        updatedAt = NOW,
                        completedAt = NOW,
                    ),
                ),
        )

    private class FakeGateway(
        initial: BackupDatabaseSnapshot,
        private val failReplace: Boolean = false,
        private var cancelReplace: Boolean = false,
    ) : BackupDataGateway {
        var current = initial

        override suspend fun snapshot(): Result<BackupDatabaseSnapshot> = Result.success(current)

        override suspend fun replace(snapshot: BackupDatabaseSnapshot): Result<Unit> =
            if (cancelReplace) {
                cancelReplace = false
                throw CancellationException()
            } else if (failReplace) {
                Result.failure(
                    IllegalStateException(),
                )
            } else {
                Result.success(Unit).also { current = snapshot }
            }
    }

    private class TestAttachmentFileStore(
        private val root: File,
    ) : AttachmentFileStore {
        override suspend fun importImage(sourceUri: android.net.Uri): Result<StoredImage> =
            Result.failure(IllegalStateException())

        override suspend fun delete(relativePath: String): Result<Unit> =
            fileFor(relativePath)?.delete().let {
                Result.success(Unit)
            }

        override suspend fun exists(relativePath: String): Boolean = fileFor(relativePath)?.isFile == true

        override suspend fun cleanupOrphans(referencedPaths: Set<String>): CleanupResult = CleanupResult(0, 0)

        override fun fileFor(relativePath: String): File? =
            if (!relativePath.startsWith("images/") || relativePath.contains("..")) null else File(root, relativePath)

        fun write(
            relativePath: String,
            bytes: ByteArray,
        ) {
            fileFor(relativePath)!!.apply {
                parentFile!!.mkdirs()
                writeBytes(bytes)
            }
        }
    }

    private class FakeSettingsRepository(
        private var failNextSave: Boolean = false,
    ) : AiSettingsRepository {
        private var current = AiSettings(useMockProvider = true)
        override val settings: Flow<AiSettings> get() = flowOf(current)

        override suspend fun getSettings(): AiSettings = current

        override suspend fun saveSettings(settings: AiSettings): Result<Unit> {
            if (failNextSave) {
                failNextSave = false
                return Result.failure(IllegalStateException())
            }
            current = settings
            return Result.success(Unit)
        }
    }

    private class FakeScheduler(
        private val failApply: Boolean = false,
    ) : AutoSummaryScheduler {
        override suspend fun applySettings(
            settings: AiSettings,
            enqueueImmediateCheck: Boolean,
        ): Result<Unit> = if (failApply) Result.failure(IllegalStateException()) else Result.success(Unit)

        override suspend fun enqueueImmediateCheck(): Result<Unit> = Result.success(Unit)

        override suspend fun enqueueGeneration(
            period: AutoSummaryPeriod,
            settings: AiSettings,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun cancelAutomaticGeneration(type: SummaryType?): Result<Unit> = Result.success(Unit)
    }

    private class FakeScheduleStateRepository : AutoSummaryScheduleStateRepository {
        override val state: Flow<AutoSummaryScheduleState> = flowOf(AutoSummaryScheduleState(null, null, 1, null))

        override suspend fun getState(): Result<AutoSummaryScheduleState> = Result.success(state.first())

        override suspend fun updateLastCheck(time: Instant): Result<Unit> = Result.success(Unit)

        override suspend fun markEvaluated(
            type: SummaryType,
            periodEnd: LocalDate,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun resetBaseline(type: SummaryType): Result<Unit> = Result.success(Unit)

        override suspend fun updateSchedulerVersion(version: Int): Result<Unit> = Result.success(Unit)
    }

    private class TestRestoreDirectoryOperations(
        private val failOnMove: Int,
    ) : RestoreDirectoryOperations {
        private val delegate = DefaultRestoreDirectoryOperations()
        private var moveCount = 0

        override fun createStageDirectory(cacheDirectory: File): File = delegate.createStageDirectory(cacheDirectory)

        override fun moveDirectory(
            source: File,
            target: File,
        ) {
            moveCount++
            if (moveCount == failOnMove) throw IOException()
            delegate.moveDirectory(source, target)
        }

        override fun deleteRecursively(directory: File): Boolean = delegate.deleteRecursively(directory)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-14T08:00:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 7, 7)
        val JPEG_HEADER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        const val STAGING_MOVE = 2
        const val ROLLBACK_OLD_MOVE = 4
    }
}
