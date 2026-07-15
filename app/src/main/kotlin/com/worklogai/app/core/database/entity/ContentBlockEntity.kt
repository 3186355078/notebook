package com.worklogai.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.worklogai.app.core.model.ContentBlockType
import java.time.Instant

@Entity(
    tableName = "content_blocks",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["entryId", "blockOrder"]),
    ],
)
data class ContentBlockEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val blockType: ContentBlockType,
    val blockOrder: Int,
    val textContent: String?,
    val structuredContent: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
