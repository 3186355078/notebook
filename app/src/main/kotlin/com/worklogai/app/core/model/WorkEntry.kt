package com.worklogai.app.core.model

import java.time.Instant
import java.time.LocalDate

data class WorkEntry(
    val id: String,
    val entryDate: LocalDate,
    val title: String?,
    val allowAiProcessing: Boolean,
    val isDeleted: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val blocks: List<ContentBlock> = emptyList(),
)

data class Attachment(
    val id: String,
    val blockId: String,
    val localPath: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int?,
    val height: Int?,
    val caption: String?,
    val createdAt: Instant,
)

data class AttachmentDraft(
    val localPath: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val caption: String? = null,
)
