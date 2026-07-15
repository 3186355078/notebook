package com.worklogai.app.core.repository

import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.AttachmentDraft
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.WorkEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Suppress("TooManyFunctions") // The entry aggregate exposes each user-visible data operation explicitly.
interface WorkEntryRepository {
    fun observeEntry(date: LocalDate): Flow<DataResult<WorkEntry?>>

    suspend fun getEntry(date: LocalDate): DataResult<WorkEntry?>

    suspend fun getEntries(
        startDate: LocalDate,
        endDate: LocalDate,
    ): DataResult<List<WorkEntry>>

    suspend fun getOrCreateEntry(date: LocalDate): DataResult<WorkEntry>

    suspend fun updateEntryTitle(
        entryId: String,
        title: String?,
    ): DataResult<Unit>

    suspend fun updateAllowAiProcessing(
        entryId: String,
        allowAiProcessing: Boolean,
    ): DataResult<Unit>

    suspend fun addTextBlock(
        entryId: String,
        text: String = "",
    ): DataResult<ContentBlock.Text>

    suspend fun addTableBlock(
        entryId: String,
        tableContent: TableContent,
    ): DataResult<ContentBlock.Table>

    suspend fun addImageBlock(entryId: String): DataResult<ContentBlock.Image>

    suspend fun addImageBlock(
        entryId: String,
        attachment: AttachmentDraft,
    ): DataResult<ContentBlock.Image>

    suspend fun updateTextBlock(
        blockId: String,
        text: String,
    ): DataResult<ContentBlock.Text>

    suspend fun updateTableBlock(
        blockId: String,
        tableContent: TableContent,
    ): DataResult<ContentBlock.Table>

    suspend fun reorderBlocks(
        entryId: String,
        orderedBlockIds: List<String>,
    ): DataResult<Unit>

    suspend fun deleteBlock(blockId: String): DataResult<List<String>>

    suspend fun addAttachment(
        blockId: String,
        draft: AttachmentDraft,
    ): DataResult<Attachment>

    suspend fun deleteAttachment(attachmentId: String): DataResult<String>

    suspend fun updateImageCaption(
        attachmentId: String,
        caption: String?,
    ): DataResult<Attachment>

    suspend fun softDeleteEntry(entryId: String): DataResult<Unit>

    suspend fun restoreEntry(entryId: String): DataResult<Unit>

    suspend fun purgeEntry(entryId: String): DataResult<List<String>>
}
