package com.worklogai.app.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity

data class ContentBlockWithAttachmentsEntity(
    @Embedded val block: ContentBlockEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "blockId",
    )
    val attachments: List<AttachmentEntity>,
)

data class WorkEntryWithContentEntity(
    @Embedded val entry: WorkEntryEntity,
    @Relation(
        entity = ContentBlockEntity::class,
        parentColumn = "id",
        entityColumn = "entryId",
    )
    val blocks: List<ContentBlockWithAttachmentsEntity>,
)
