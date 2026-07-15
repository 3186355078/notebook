package com.worklogai.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ContentBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["blockId"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val blockId: String,
    val localPath: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int?,
    val height: Int?,
    val caption: String?,
    val createdAt: Instant,
)
