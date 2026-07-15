package com.worklogai.app.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.codec.TableContentCodec
import com.worklogai.app.core.database.dao.AttachmentDao
import com.worklogai.app.core.database.dao.ContentBlockDao
import com.worklogai.app.core.database.dao.WorkEntryDao
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.mapper.toDomain
import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.AttachmentDraft
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.repository.WorkEntryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@Suppress("TooManyFunctions") // Implements the intentionally explicit WorkEntryRepository aggregate API.
class OfflineWorkEntryRepository
    @Inject
    @Suppress("LongParameterList") // Dependencies mirror the three-table entry aggregate boundary.
    constructor(
        private val database: WorkLogDatabase,
        private val workEntryDao: WorkEntryDao,
        private val contentBlockDao: ContentBlockDao,
        private val attachmentDao: AttachmentDao,
        private val tableContentCodec: TableContentCodec,
        private val idGenerator: IdGenerator,
        private val timeProvider: TimeProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WorkEntryRepository {
        override fun observeEntry(date: LocalDate): Flow<DataResult<WorkEntry?>> =
            workEntryDao
                .observeActiveWithContentByDate(date)
                .map { relation -> relation?.toDomain(tableContentCodec) ?: DataResult.Success(null) }
                .catchDatabaseErrors()
                .flowOn(ioDispatcher)

        override suspend fun getEntry(date: LocalDate): DataResult<WorkEntry?> =
            databaseResult {
                workEntryDao
                    .getActiveWithContentByDate(date)
                    ?.toDomain(tableContentCodec)
                    ?: DataResult.Success(null)
            }

        override suspend fun getEntries(
            startDate: LocalDate,
            endDate: LocalDate,
        ): DataResult<List<WorkEntry>> {
            if (startDate > endDate) return invalid(DataValidationReason.INVALID_DATE_RANGE)

            return databaseResult {
                val entries = mutableListOf<WorkEntry>()
                workEntryDao
                    .getActiveWithContentInDateRange(startDate, endDate)
                    .forEach { relation ->
                        when (val result = relation.toDomain(tableContentCodec)) {
                            is DataResult.Failure -> return@databaseResult result
                            is DataResult.Success -> entries += result.value
                        }
                    }
                DataResult.Success(entries)
            }
        }

        override suspend fun getOrCreateEntry(date: LocalDate): DataResult<WorkEntry> =
            databaseResult {
                database.withTransaction {
                    val existing = workEntryDao.getWithContentByDateIncludingDeleted(date)
                    when {
                        existing == null -> createEntry(date)
                        existing.entry.isDeleted -> {
                            workEntryDao.restore(existing.entry.id, timeProvider.now())
                            entryForDate(date)
                        }

                        else -> existing.toDomain(tableContentCodec)
                    }
                }
            }

        override suspend fun updateEntryTitle(
            entryId: String,
            title: String?,
        ): DataResult<Unit> =
            databaseResult {
                val updated = workEntryDao.updateTitle(entryId, title.normalizeTitle(), timeProvider.now())
                if (updated == 1) DataResult.Success(Unit) else DataResult.Failure(DataError.NotFound)
            }

        override suspend fun updateAllowAiProcessing(
            entryId: String,
            allowAiProcessing: Boolean,
        ): DataResult<Unit> =
            databaseResult {
                val updated = workEntryDao.updateAllowAiProcessing(entryId, allowAiProcessing, timeProvider.now())
                if (updated == 1) DataResult.Success(Unit) else DataResult.Failure(DataError.NotFound)
            }

        override suspend fun addTextBlock(
            entryId: String,
            text: String,
        ): DataResult<ContentBlock.Text> =
            databaseResult {
                addBlock(
                    entryId = entryId,
                    blockType = ContentBlockType.TEXT,
                    textContent = text,
                    structuredContent = null,
                )
            }.asTextBlock()

        override suspend fun addTableBlock(
            entryId: String,
            tableContent: TableContent,
        ): DataResult<ContentBlock.Table> =
            when (val encoded = tableContentCodec.encode(tableContent)) {
                is DataResult.Failure -> encoded
                is DataResult.Success ->
                    databaseResult {
                        addBlock(
                            entryId = entryId,
                            blockType = ContentBlockType.TABLE,
                            textContent = null,
                            structuredContent = encoded.value,
                        )
                    }.asTableBlock()
            }

        override suspend fun addImageBlock(entryId: String): DataResult<ContentBlock.Image> =
            databaseResult {
                addBlock(
                    entryId = entryId,
                    blockType = ContentBlockType.IMAGE,
                    textContent = null,
                    structuredContent = null,
                )
            }.asImageBlock()

        override suspend fun addImageBlock(
            entryId: String,
            attachment: AttachmentDraft,
        ): DataResult<ContentBlock.Image> {
            if (!attachment.isValid()) return invalid(DataValidationReason.INVALID_ATTACHMENT)
            return databaseResult {
                database.withTransaction {
                    val imageBlock =
                        when (val result = addBlock(entryId, ContentBlockType.IMAGE, null, null)) {
                            is DataResult.Failure -> return@withTransaction result
                            is DataResult.Success ->
                                result.value as? ContentBlock.Image
                                    ?: return@withTransaction invalidBlockContent()
                        }
                    attachmentDao.insert(
                        AttachmentEntity(
                            id = idGenerator.generate(),
                            blockId = imageBlock.id,
                            localPath = attachment.localPath,
                            mimeType = attachment.mimeType,
                            fileSize = attachment.fileSize,
                            width = attachment.width,
                            height = attachment.height,
                            caption = attachment.caption.normalizeCaption(),
                            createdAt = timeProvider.now(),
                        ),
                    )
                    blockForId(imageBlock.id)
                }
            }.asImageBlock()
        }

        override suspend fun updateTextBlock(
            blockId: String,
            text: String,
        ): DataResult<ContentBlock.Text> =
            databaseResult {
                database.withTransaction {
                    val existing = contentBlockDao.getById(blockId) ?: return@withTransaction notFound()
                    if (existing.blockType != ContentBlockType.TEXT) return@withTransaction invalidBlockContent()

                    contentBlockDao.update(
                        existing.copy(
                            textContent = text,
                            structuredContent = null,
                            updatedAt = timeProvider.now(),
                        ),
                    )
                    blockForId(blockId)
                }
            }.asTextBlock()

        override suspend fun updateTableBlock(
            blockId: String,
            tableContent: TableContent,
        ): DataResult<ContentBlock.Table> =
            when (val encoded = tableContentCodec.encode(tableContent)) {
                is DataResult.Failure -> encoded
                is DataResult.Success ->
                    databaseResult {
                        database.withTransaction {
                            val existing = contentBlockDao.getById(blockId) ?: return@withTransaction notFound()
                            if (existing.blockType != ContentBlockType.TABLE) {
                                return@withTransaction invalidBlockContent()
                            }

                            contentBlockDao.update(
                                existing.copy(
                                    textContent = null,
                                    structuredContent = encoded.value,
                                    updatedAt = timeProvider.now(),
                                ),
                            )
                            blockForId(blockId)
                        }
                    }.asTableBlock()
            }

        override suspend fun reorderBlocks(
            entryId: String,
            orderedBlockIds: List<String>,
        ): DataResult<Unit> =
            databaseResult {
                database.withTransaction {
                    val existingIds = contentBlockDao.getByEntryId(entryId).map(ContentBlockEntity::id)
                    val isCompletePermutation =
                        orderedBlockIds.size == existingIds.size &&
                            orderedBlockIds.distinct().size == orderedBlockIds.size &&
                            orderedBlockIds.toSet() == existingIds.toSet()
                    if (!isCompletePermutation) return@withTransaction invalid(DataValidationReason.INVALID_ORDERING)

                    contentBlockDao.updateOrders(entryId, orderedBlockIds, timeProvider.now())
                    DataResult.Success(Unit)
                }
            }

        override suspend fun deleteBlock(blockId: String): DataResult<List<String>> =
            databaseResult {
                database.withTransaction {
                    val block = contentBlockDao.getById(blockId) ?: return@withTransaction notFound()
                    val paths = attachmentDao.getPathsByBlockId(block.id)
                    contentBlockDao.delete(block)
                    DataResult.Success(paths)
                }
            }

        override suspend fun addAttachment(
            blockId: String,
            draft: AttachmentDraft,
        ): DataResult<Attachment> {
            if (!draft.isValid()) return invalid(DataValidationReason.INVALID_ATTACHMENT)

            return databaseResult {
                database.withTransaction {
                    val block = contentBlockDao.getById(blockId) ?: return@withTransaction notFound()
                    if (block.blockType != ContentBlockType.IMAGE) return@withTransaction invalidBlockContent()

                    val attachment =
                        AttachmentEntity(
                            id = idGenerator.generate(),
                            blockId = blockId,
                            localPath = draft.localPath,
                            mimeType = draft.mimeType,
                            fileSize = draft.fileSize,
                            width = draft.width,
                            height = draft.height,
                            caption = draft.caption.normalizeCaption(),
                            createdAt = timeProvider.now(),
                        )
                    attachmentDao.insert(attachment)
                    DataResult.Success(attachment.toDomain())
                }
            }
        }

        override suspend fun deleteAttachment(attachmentId: String): DataResult<String> =
            databaseResult {
                val attachment = attachmentDao.getById(attachmentId) ?: return@databaseResult notFound()
                attachmentDao.delete(attachment)
                DataResult.Success(attachment.localPath)
            }

        override suspend fun updateImageCaption(
            attachmentId: String,
            caption: String?,
        ): DataResult<Attachment> =
            databaseResult {
                val attachment = attachmentDao.getById(attachmentId) ?: return@databaseResult notFound()
                attachmentDao.update(attachment.copy(caption = caption.normalizeCaption()))
                DataResult.Success(attachment.copy(caption = caption.normalizeCaption()).toDomain())
            }

        override suspend fun softDeleteEntry(entryId: String): DataResult<Unit> =
            databaseResult {
                val entry = workEntryDao.getByIdIncludingDeleted(entryId) ?: return@databaseResult notFound()
                if (entry.isDeleted) return@databaseResult DataResult.Success(Unit)

                workEntryDao.softDelete(entryId, timeProvider.now())
                DataResult.Success(Unit)
            }

        override suspend fun restoreEntry(entryId: String): DataResult<Unit> =
            databaseResult {
                val entry = workEntryDao.getByIdIncludingDeleted(entryId) ?: return@databaseResult notFound()
                if (!entry.isDeleted) return@databaseResult DataResult.Success(Unit)

                workEntryDao.restore(entryId, timeProvider.now())
                DataResult.Success(Unit)
            }

        override suspend fun purgeEntry(entryId: String): DataResult<List<String>> =
            databaseResult {
                database.withTransaction {
                    val entry = workEntryDao.getByIdIncludingDeleted(entryId) ?: return@withTransaction notFound()
                    val paths = attachmentDao.getPathsByEntryId(entryId)
                    workEntryDao.delete(entry)
                    DataResult.Success(paths)
                }
            }

        private suspend fun createEntry(date: LocalDate): DataResult<WorkEntry> {
            val now = timeProvider.now()
            val entry =
                WorkEntryEntity(
                    id = idGenerator.generate(),
                    entryDate = date,
                    title = null,
                    allowAiProcessing = true,
                    isDeleted = false,
                    createdAt = now,
                    updatedAt = now,
                )
            try {
                workEntryDao.insert(entry)
            } catch (_: SQLiteConstraintException) {
                return entryForDate(date)
            }
            return entryForDate(date)
        }

        private suspend fun addBlock(
            entryId: String,
            blockType: ContentBlockType,
            textContent: String?,
            structuredContent: String?,
        ): DataResult<ContentBlock> =
            database.withTransaction {
                if (workEntryDao.getActiveById(entryId) == null) return@withTransaction notFound()

                val now = timeProvider.now()
                val block =
                    ContentBlockEntity(
                        id = idGenerator.generate(),
                        entryId = entryId,
                        blockType = blockType,
                        blockOrder = contentBlockDao.nextBlockOrder(entryId),
                        textContent = textContent,
                        structuredContent = structuredContent,
                        createdAt = now,
                        updatedAt = now,
                    )
                contentBlockDao.insert(block)
                blockForId(block.id)
            }

        private suspend fun entryForDate(date: LocalDate): DataResult<WorkEntry> =
            workEntryDao
                .getWithContentByDateIncludingDeleted(date)
                ?.toDomain(tableContentCodec)
                ?: notFound()

        private suspend fun blockForId(blockId: String): DataResult<ContentBlock> =
            contentBlockDao
                .getWithAttachments(blockId)
                ?.toDomain(tableContentCodec)
                ?: notFound()

        private suspend fun <T> databaseResult(operation: suspend () -> DataResult<T>): DataResult<T> =
            withContext(ioDispatcher) {
                try {
                    operation()
                } catch (_: SQLiteConstraintException) {
                    DataResult.Failure(DataError.Conflict)
                } catch (_: SQLiteException) {
                    DataResult.Failure(DataError.Storage)
                }
            }

        private fun Flow<DataResult<WorkEntry?>>.catchDatabaseErrors(): Flow<DataResult<WorkEntry?>> =
            catch { error ->
                currentCoroutineContext().ensureActive()
                val dataError = if (error is SQLiteConstraintException) DataError.Conflict else DataError.Storage
                emit(DataResult.Failure(dataError))
            }
    }

private fun String?.normalizeTitle(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String?.normalizeCaption(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun AttachmentDraft.isValid(): Boolean =
    localPath.isNotBlank() &&
        mimeType.isNotBlank() &&
        fileSize >= 0 &&
        (width == null || width > 0) &&
        (height == null || height > 0)

private fun <T> notFound(): DataResult<T> = DataResult.Failure(DataError.NotFound)

private fun <T> invalid(reason: DataValidationReason): DataResult<T> = DataResult.Failure(DataError.Validation(reason))

private fun <T> invalidBlockContent(): DataResult<T> = invalid(DataValidationReason.INVALID_BLOCK_CONTENT)

private fun DataResult<ContentBlock>.asTextBlock(): DataResult<ContentBlock.Text> =
    when (this) {
        is DataResult.Failure -> this
        is DataResult.Success ->
            (value as? ContentBlock.Text)
                ?.let { block -> DataResult.Success(block) }
                ?: invalidBlockContent()
    }

private fun DataResult<ContentBlock>.asImageBlock(): DataResult<ContentBlock.Image> =
    when (this) {
        is DataResult.Failure -> this
        is DataResult.Success ->
            (value as? ContentBlock.Image)
                ?.let { block -> DataResult.Success(block) }
                ?: invalidBlockContent()
    }

private fun DataResult<ContentBlock>.asTableBlock(): DataResult<ContentBlock.Table> =
    when (this) {
        is DataResult.Failure -> this
        is DataResult.Success ->
            (value as? ContentBlock.Table)
                ?.let { block -> DataResult.Success(block) }
                ?: invalidBlockContent()
    }
