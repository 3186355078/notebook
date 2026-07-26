package com.worklogai.app.core.backup

import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.codec.TableContentCodec
import com.worklogai.app.core.history.WorkPeriodCalculator
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TODO_COMPLETION_NOTE_MAX_LENGTH
import com.worklogai.app.core.model.TODO_NOTE_MAX_LENGTH
import com.worklogai.app.core.model.TODO_TITLE_MAX_LENGTH
import com.worklogai.app.core.model.TodoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.DateTimeException
import java.time.Instant
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

internal class BackupArchiveReader internal constructor(
    private val tableContentCodec: TableContentCodec,
    private val workPeriodCalculator: WorkPeriodCalculator,
    private val safetyLimits: BackupSafetyLimits,
) {
    @Inject
    constructor(
        tableContentCodec: TableContentCodec,
        workPeriodCalculator: WorkPeriodCalculator,
    ) : this(tableContentCodec, workPeriodCalculator, BackupSafetyLimits())

    fun readArchive(
        source: InputStream,
        cacheDirectory: File,
    ): ArchiveReadResult {
        val temporaryFile =
            File
                .createTempFile(
                    "restore",
                    BackupArchiveContract.ZIP_EXTENSION,
                    cacheDirectory.apply {
                        mkdirs()
                    },
                )
        return try {
            BackupArchiveStreams.copyToFile(source, temporaryFile, safetyLimits.maxArchiveSizeBytes)
            ArchiveReadResult.Success(parseArchive(temporaryFile))
        } catch (error: CancellationException) {
            temporaryFile.delete()
            throw error
        } catch (_: IOException) {
            temporaryFile.delete()
            ArchiveReadResult.Failure("备份文件格式不正确")
        } catch (_: IllegalArgumentException) {
            temporaryFile.delete()
            ArchiveReadResult.Failure("备份文件格式不正确")
        } catch (_: DateTimeException) {
            temporaryFile.delete()
            ArchiveReadResult.Failure("备份文件格式不正确")
        } catch (_: SerializationException) {
            temporaryFile.delete()
            ArchiveReadResult.Failure("备份文件格式不正确")
        }
    }

    private fun parseArchive(file: File): ParsedArchive =
        ZipFile(file).use { zip ->
            val allEntries =
                buildList {
                    val enumeration = zip.entries()
                    while (enumeration.hasMoreElements()) add(enumeration.nextElement())
                }
            BackupArchiveEntryValidator.validate(
                allEntries.map(ZipEntry::toMetadata),
                safetyLimits,
            )
            val entries = allEntries.filterNot(ZipEntry::isDirectory)
            val byName = entries.associateBy(ZipEntry::getName)
            val manifest =
                BackupArchiveValidation.readManifest(
                    zip,
                    BackupArchivePaths.run { byName.required(BackupArchiveContract.MANIFEST_FILE) },
                    safetyLimits.maxJsonSizeBytes,
                )
            validateManifest(manifest)
            val payload =
                BackupPayload(
                    BackupArchiveValidation.readJson(
                        zip,
                        BackupArchivePaths.run { byName.required(BackupArchiveContract.ENTRIES_FILE) },
                        safetyLimits.maxJsonSizeBytes,
                    ),
                    BackupArchiveValidation.readJson(
                        zip,
                        BackupArchivePaths.run { byName.required(BackupArchiveContract.BLOCKS_FILE) },
                        safetyLimits.maxJsonSizeBytes,
                    ),
                    BackupArchiveValidation.readJson(
                        zip,
                        BackupArchivePaths.run { byName.required(BackupArchiveContract.ATTACHMENTS_FILE) },
                        safetyLimits.maxJsonSizeBytes,
                    ),
                    BackupArchiveValidation.readJson(
                        zip,
                        BackupArchivePaths.run { byName.required(BackupArchiveContract.SUMMARIES_FILE) },
                        safetyLimits.maxJsonSizeBytes,
                    ),
                    BackupArchiveValidation.readJson(
                        zip,
                        BackupArchivePaths.run { byName.required(BackupArchiveContract.SETTINGS_FILE) },
                        safetyLimits.maxJsonSizeBytes,
                    ),
                    if (manifest.formatVersion >= BACKUP_FORMAT_VERSION) {
                        BackupArchiveValidation.readJson(
                            zip,
                            BackupArchivePaths.run { byName.required(BackupArchiveContract.TODOS_FILE) },
                            safetyLimits.maxJsonSizeBytes,
                        )
                    } else {
                        emptyList()
                    },
                )
            validateDeclaredFiles(zip, byName, manifest)
            validatePayload(payload, manifest)
            validateAttachmentHeaders(zip, byName, payload)
            ParsedArchive(
                file,
                payload,
                BackupArchiveValidation.toPreview(manifest, payload.attachments.count { !it.fileIncluded }),
            )
        }

    private fun validateManifest(manifest: BackupManifest) {
        require(manifest.formatName == BACKUP_FORMAT_NAME)
        require(manifest.formatVersion in MIN_SUPPORTED_BACKUP_FORMAT_VERSION..BACKUP_FORMAT_VERSION)
        require(
            when (manifest.formatVersion) {
                1 -> manifest.databaseVersion == 1
                BACKUP_FORMAT_VERSION -> manifest.databaseVersion == WorkLogDatabase.VERSION
                else -> false
            },
        )
        Instant.parse(manifest.createdAt)
        require(manifest.entryCount in 0..safetyLimits.maxEntries)
        require(manifest.blockCount in 0..safetyLimits.maxBlocks)
        require(manifest.attachmentCount in 0..safetyLimits.maxAttachments)
        require(manifest.summaryCount in 0..safetyLimits.maxSummaries)
        require(manifest.todoCount in 0..safetyLimits.maxTodos)
        if (manifest.formatVersion == 1) {
            require(manifest.todoCount == 0)
            require(manifest.files.none { it.path == BackupArchiveContract.TODOS_FILE })
        } else {
            require(manifest.files.any { it.path == BackupArchiveContract.TODOS_FILE })
        }
        require(
            manifest.files
                .map(BackupFileManifest::path)
                .distinct()
                .size == manifest.files.size,
        )
        require(
            manifest.files.all { file ->
                BackupArchivePaths.run { file.path.isKnownBackupPath(safetyLimits.maxPathLength) } &&
                    file.size >= 0 &&
                    BackupArchiveValidation.isSha256(file.sha256)
            },
        )
    }

    private fun validateDeclaredFiles(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        manifest: BackupManifest,
    ) {
        var totalUncompressedBytes = 0L
        manifest.files.forEach { file ->
            val entry = BackupArchivePaths.run { entries.required(file.path) }
            val bytes =
                zip.getInputStream(entry).use {
                    BackupArchiveStreams.readBytes(it, safetyLimits.maxSingleEntrySizeBytes)
                }
            totalUncompressedBytes += bytes.size
            require(totalUncompressedBytes <= safetyLimits.maxTotalUncompressedSizeBytes)
            require(bytes.size.toLong() == file.size)
            require(BackupArchiveStreams.sha256(bytes).equals(file.sha256, ignoreCase = true))
        }
        val allowed = manifest.files.map(BackupFileManifest::path).toSet() + BackupArchiveContract.MANIFEST_FILE
        require(entries.keys == allowed)
        require(manifest.files.none { it.path == BackupArchiveContract.MANIFEST_FILE })
    }

    private fun validatePayload(
        payload: BackupPayload,
        manifest: BackupManifest,
    ) {
        require(payload.entries.size == manifest.entryCount)
        require(payload.blocks.size == manifest.blockCount)
        require(payload.attachments.size == manifest.attachmentCount)
        require(payload.summaries.size == manifest.summaryCount)
        require(payload.todos.size == manifest.todoCount)
        BackupArchiveValidation.requireUnique(payload.entries.map(BackupWorkEntry::id))
        BackupArchiveValidation.requireUnique(payload.entries.map(BackupWorkEntry::entryDate))
        BackupArchiveValidation.requireUnique(payload.blocks.map(BackupContentBlock::id))
        BackupArchiveValidation.requireUnique(payload.attachments.map(BackupAttachment::id))
        BackupArchiveValidation.requireUnique(payload.summaries.map(BackupWorkSummary::id))
        BackupArchiveValidation.requireUnique(payload.todos.map(BackupTodoItem::id))
        BackupArchiveValidation.requireUnique(
            payload.summaries.map { "${it.summaryType}:${it.periodStart}:${it.periodEnd}" },
        )
        val entryIds = payload.entries.map(BackupWorkEntry::id).toSet()
        val blocksById = payload.blocks.associateBy(BackupContentBlock::id)
        require(payload.entries.all(::isValidEntry))
        require(payload.blocks.all { it.entryId in entryIds && it.blockOrder >= 0 })
        require(
            payload.blocks.groupBy(BackupContentBlock::entryId).values.all { blocks ->
                blocks.map(BackupContentBlock::blockOrder).sorted() == blocks.indices.toList()
            },
        )
        require(
            payload.attachments.all { attachment ->
                blocksById[attachment.blockId]?.blockType == ContentBlockType.IMAGE.name &&
                    BackupArchivePaths.run { attachment.localPath.isManagedAttachmentPath() }
            },
        )
        val filesByPath = manifest.files.associateBy(BackupFileManifest::path)
        payload.attachments.forEach { attachment ->
            require(attachment.width == null || attachment.width > 0)
            require(attachment.height == null || attachment.height > 0)
            require(attachment.caption == null || attachment.caption.length <= MAX_CAPTION_LENGTH)
            val declared = filesByPath[BackupArchivePaths.attachmentEntryPath(attachment.localPath)]
            if (attachment.fileIncluded) {
                require(declared != null && declared.size == attachment.fileSize)
                require(attachment.fileSize in 1..safetyLimits.maxAttachmentSizeBytes)
                require(attachment.mimeType in BackupArchiveContract.SUPPORTED_MIME_TYPES)
            } else {
                require(declared == null)
            }
        }
        val attachmentBlockIds = payload.attachments.map(BackupAttachment::blockId).toSet()
        payload.blocks.forEach(::validateBlock)
        require(
            payload.blocks.filter { it.blockType == ContentBlockType.IMAGE.name }.all {
                it.id in
                    attachmentBlockIds
            },
        )
        payload.entries.forEach { entry -> require(entry.toEntity().createdAt <= entry.toEntity().updatedAt) }
        payload.blocks.forEach { block -> require(block.toEntity().createdAt <= block.toEntity().updatedAt) }
        payload.summaries.forEach(::validateSummary)
        payload.todos.forEach { todo -> validateTodo(todo, blocksById) }
        payload.settings.toAiSettings()
    }

    private fun validateAttachmentHeaders(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        payload: BackupPayload,
    ) {
        payload.attachments.filter(BackupAttachment::fileIncluded).forEach { attachment ->
            val entry = BackupArchivePaths.run { entries.required(attachmentEntryPath(attachment.localPath)) }
            val header = zip.getInputStream(entry).use(BackupArchiveStreams::readImageHeader)
            require(BackupArchiveStreams.matchesImageMimeType(header, attachment.mimeType))
        }
    }

    private fun validateBlock(block: BackupContentBlock) {
        when (ContentBlockType.valueOf(block.blockType)) {
            ContentBlockType.TEXT -> require(block.textContent != null && block.structuredContent == null)
            ContentBlockType.IMAGE -> require(block.textContent == null && block.structuredContent == null)
            ContentBlockType.TABLE -> {
                require(block.textContent == null && block.structuredContent != null)
                val decoded = tableContentCodec.decode(block.structuredContent)
                require(decoded is DataResult.Success)
                require(decoded.value.columns.size in 1..BackupArchiveContract.MAX_TABLE_COLUMNS)
                require(decoded.value.rows.size in 1..BackupArchiveContract.MAX_TABLE_ROWS)
                require(
                    decoded.value.columns
                        .map { it.id }
                        .distinct()
                        .size == decoded.value.columns.size,
                )
                require(
                    decoded.value.rows
                        .map { it.id }
                        .distinct()
                        .size == decoded.value.rows.size,
                )
                val columnIds =
                    decoded.value.columns
                        .map { it.id }
                        .toSet()
                require(decoded.value.rows.all { it.cells.keys == columnIds })
            }
        }
    }

    private fun validateSummary(summary: BackupWorkSummary) {
        val entity = summary.toEntity()
        require(entity.periodStart <= entity.periodEnd)
        require(entity.createdAt <= entity.updatedAt)
        require(entity.sourceHash == null || BackupArchiveValidation.isSha256(entity.sourceHash))
        require(
            entity.status != com.worklogai.app.core.model.SummaryStatus.SUCCESS ||
                !entity.originalContent.isNullOrBlank() ||
                !entity.editedContent.isNullOrBlank(),
        )
        val expected =
            when (entity.summaryType) {
                SummaryType.WEEKLY -> workPeriodCalculator.weekContaining(entity.periodStart)
                SummaryType.MONTHLY -> workPeriodCalculator.monthContaining(YearMonth.from(entity.periodStart))
            }
        require(expected.start == entity.periodStart && expected.end == entity.periodEnd)
    }

    private fun isValidEntry(entry: BackupWorkEntry): Boolean =
        entry.id.isNotBlank() &&
            entry.title.orEmpty().length <= MAX_TITLE_LENGTH &&
            entry.title.orEmpty().none { it.code < CONTROL_CHARACTER_LIMIT }

    private fun validateTodo(
        todo: BackupTodoItem,
        blocksById: Map<String, BackupContentBlock>,
    ) {
        val entity = todo.toEntity()
        require(entity.id.isNotBlank())
        require(entity.title.isNotBlank() && entity.title == entity.title.trim())
        require(entity.title.length <= TODO_TITLE_MAX_LENGTH)
        require(entity.title.none(Char::isISOControl))
        require(entity.note.orEmpty().length <= TODO_NOTE_MAX_LENGTH)
        require(entity.completionNote.orEmpty().length <= TODO_COMPLETION_NOTE_MAX_LENGTH)
        require(entity.note.isSafeMultilineText())
        require(entity.completionNote.isSafeMultilineText())
        require(entity.sortOrder >= 0)
        require(
            entity.linkedContentBlockId == null ||
                blocksById[entity.linkedContentBlockId]?.blockType == ContentBlockType.TEXT.name,
        )
        require(entity.createdAt <= entity.updatedAt)
        when (entity.status) {
            TodoStatus.DONE -> {
                require(entity.completedAt != null)
                require(entity.completedAt >= entity.createdAt)
                require(entity.completedAt <= entity.updatedAt)
            }

            TodoStatus.NOT_STARTED,
            TodoStatus.IN_PROGRESS,
            TodoStatus.CANCELED,
            -> require(entity.completedAt == null)
        }
    }
}

private fun String?.isSafeMultilineText(): Boolean =
    orEmpty().none { character ->
        character.isISOControl() &&
            character != '\n' &&
            character != '\r' &&
            character != '\t'
    }

internal object BackupArchiveValidation {
    fun readManifest(
        zip: ZipFile,
        entry: ZipEntry,
        maxSizeBytes: Long,
    ): BackupManifest {
        val jsonElement =
            backupJson.parseToJsonElement(
                zip
                    .getInputStream(entry)
                    .use {
                        BackupArchiveStreams.readBytes(it, maxSizeBytes)
                    }.decodeToString(),
            )
        val manifest = backupJson.decodeFromJsonElement<BackupManifest>(jsonElement)
        require(manifest.formatVersion < BACKUP_FORMAT_VERSION || "todoCount" in jsonElement.jsonObject)
        return manifest
    }

    fun toPreview(
        manifest: BackupManifest,
        warningCount: Int,
    ): BackupPreview =
        BackupPreview(
            createdAt = Instant.parse(manifest.createdAt),
            entryCount = manifest.entryCount,
            blockCount = manifest.blockCount,
            attachmentCount = manifest.attachmentCount,
            summaryCount = manifest.summaryCount,
            warningCount = warningCount,
            todoCount = manifest.todoCount,
        )

    inline fun <reified T> readJson(
        zip: ZipFile,
        entry: ZipEntry,
        maxSizeBytes: Long,
    ): T =
        backupJson.decodeFromString(
            zip
                .getInputStream(entry)
                .use {
                    BackupArchiveStreams.readBytes(it, maxSizeBytes)
                }.decodeToString(),
        )

    fun isSha256(value: String): Boolean = value.matches(Regex("(?i)[a-f0-9]{64}"))

    fun requireUnique(values: List<String>) {
        require(values.all(String::isNotBlank) && values.distinct().size == values.size)
    }
}

internal sealed interface ArchiveReadResult {
    suspend fun <T> useResult(transform: suspend (ParsedArchive) -> BackupOperationResult<T>): BackupOperationResult<T>

    data class Success(
        val archive: ParsedArchive,
    ) : ArchiveReadResult {
        override suspend fun <T> useResult(
            transform: suspend (ParsedArchive) -> BackupOperationResult<T>,
        ): BackupOperationResult<T> =
            try {
                transform(archive)
            } finally {
                archive.file.delete()
            }
    }

    data class Failure(
        val message: String,
    ) : ArchiveReadResult {
        override suspend fun <T> useResult(
            transform: suspend (ParsedArchive) -> BackupOperationResult<T>,
        ): BackupOperationResult<T> = BackupOperationResult.Failure(message)
    }
}

internal data class ParsedArchive(
    val file: File,
    val payload: BackupPayload,
    val preview: BackupPreview,
)

private fun ZipEntry.toMetadata(): BackupZipEntryMetadata =
    BackupZipEntryMetadata(
        name = name,
        isDirectory = isDirectory,
        size = size,
        compressedSize = compressedSize,
    )

private const val CONTROL_CHARACTER_LIMIT = 0x20
private const val MAX_TITLE_LENGTH = 512
private const val MAX_CAPTION_LENGTH = 4_000
