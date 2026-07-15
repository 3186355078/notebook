package com.worklogai.app.core.database.mapper

import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.database.codec.TableContentCodec
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.database.relation.ContentBlockWithAttachmentsEntity
import com.worklogai.app.core.database.relation.WorkEntryWithContentEntity
import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.model.WorkSummary

fun WorkEntryEntity.toDomain(blocks: List<ContentBlock> = emptyList()): WorkEntry =
    WorkEntry(
        id = id,
        entryDate = entryDate,
        title = title,
        allowAiProcessing = allowAiProcessing,
        isDeleted = isDeleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        blocks = blocks,
    )

fun WorkEntryWithContentEntity.toDomain(codec: TableContentCodec): DataResult<WorkEntry> {
    val blocks = mutableListOf<ContentBlock>()
    this.blocks
        .sortedWith(
            compareBy<ContentBlockWithAttachmentsEntity> { it.block.blockOrder }
                .thenBy { it.block.createdAt }
                .thenBy { it.block.id },
        ).forEach { relation ->
            when (val result = relation.toDomain(codec)) {
                is DataResult.Failure -> return result
                is DataResult.Success -> blocks += result.value
            }
        }
    return DataResult.Success(entry.toDomain(blocks))
}

fun AttachmentEntity.toDomain(): Attachment =
    Attachment(
        id = id,
        blockId = blockId,
        localPath = localPath,
        mimeType = mimeType,
        fileSize = fileSize,
        width = width,
        height = height,
        caption = caption,
        createdAt = createdAt,
    )

fun WorkSummaryEntity.toDomain(): WorkSummary =
    WorkSummary(
        id = id,
        summaryType = summaryType,
        periodStart = periodStart,
        periodEnd = periodEnd,
        status = status,
        sourceHash = sourceHash,
        aiProvider = aiProvider,
        modelName = modelName,
        originalContent = originalContent,
        editedContent = editedContent,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        generatedAt = generatedAt,
    )

fun WorkSummary.toEntity(): WorkSummaryEntity =
    WorkSummaryEntity(
        id = id,
        summaryType = summaryType,
        periodStart = periodStart,
        periodEnd = periodEnd,
        status = status,
        sourceHash = sourceHash,
        aiProvider = aiProvider,
        modelName = modelName,
        originalContent = originalContent,
        editedContent = editedContent,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        generatedAt = generatedAt,
    )
