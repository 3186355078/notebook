package com.worklogai.app.core.model

import java.time.Instant

enum class ContentBlockType {
    TEXT,
    IMAGE,
    TABLE,
}

sealed interface ContentBlock {
    val id: String
    val entryId: String
    val order: Int
    val createdAt: Instant
    val updatedAt: Instant
    val type: ContentBlockType

    data class Text(
        override val id: String,
        override val entryId: String,
        override val order: Int,
        val content: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
    ) : ContentBlock {
        override val type: ContentBlockType = ContentBlockType.TEXT
    }

    data class Image(
        override val id: String,
        override val entryId: String,
        override val order: Int,
        val attachments: List<Attachment>,
        override val createdAt: Instant,
        override val updatedAt: Instant,
    ) : ContentBlock {
        override val type: ContentBlockType = ContentBlockType.IMAGE
    }

    data class Table(
        override val id: String,
        override val entryId: String,
        override val order: Int,
        val content: TableContent,
        override val createdAt: Instant,
        override val updatedAt: Instant,
    ) : ContentBlock {
        override val type: ContentBlockType = ContentBlockType.TABLE
    }
}
