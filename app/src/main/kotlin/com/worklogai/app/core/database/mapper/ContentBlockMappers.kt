package com.worklogai.app.core.database.mapper

import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.database.codec.TableContentCodec
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.relation.ContentBlockWithAttachmentsEntity
import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.ContentBlockType

fun ContentBlockWithAttachmentsEntity.toDomain(codec: TableContentCodec): DataResult<ContentBlock> =
    when (block.blockType) {
        ContentBlockType.IMAGE -> toImageBlock()
        ContentBlockType.TABLE -> toTableBlock(codec)
        ContentBlockType.TEXT -> toTextBlock()
    }

private fun ContentBlockWithAttachmentsEntity.toImageBlock(): DataResult<ContentBlock> =
    if (block.textContent != null || block.structuredContent != null) {
        invalidBlockContent()
    } else {
        DataResult.Success(
            ContentBlock.Image(
                id = block.id,
                entryId = block.entryId,
                order = block.blockOrder,
                attachments = sortedAttachments(),
                createdAt = block.createdAt,
                updatedAt = block.updatedAt,
            ),
        )
    }

private fun ContentBlockWithAttachmentsEntity.toTableBlock(codec: TableContentCodec): DataResult<ContentBlock> =
    if (block.textContent != null) {
        invalidBlockContent()
    } else {
        block.structuredContent?.let { content ->
            codec.decode(content).map { table ->
                ContentBlock.Table(
                    id = block.id,
                    entryId = block.entryId,
                    order = block.blockOrder,
                    content = table,
                    createdAt = block.createdAt,
                    updatedAt = block.updatedAt,
                )
            }
        } ?: invalidBlockContent()
    }

private fun ContentBlockWithAttachmentsEntity.toTextBlock(): DataResult<ContentBlock> =
    block.textContent
        ?.takeIf { block.structuredContent == null }
        ?.let { content ->
            DataResult.Success(
                ContentBlock.Text(
                    id = block.id,
                    entryId = block.entryId,
                    order = block.blockOrder,
                    content = content,
                    createdAt = block.createdAt,
                    updatedAt = block.updatedAt,
                ),
            )
        } ?: invalidBlockContent()

private fun ContentBlockWithAttachmentsEntity.sortedAttachments(): List<Attachment> =
    attachments
        .sortedWith(compareBy<AttachmentEntity> { it.createdAt }.thenBy { it.id })
        .map(AttachmentEntity::toDomain)

private fun invalidBlockContent(): DataResult.Failure =
    DataResult.Failure(DataError.Validation(DataValidationReason.INVALID_BLOCK_CONTENT))

private inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> =
    when (this) {
        is DataResult.Failure -> this
        is DataResult.Success -> DataResult.Success(transform(value))
    }
