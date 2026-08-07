package com.worklogai.app.core.model

sealed interface NewWorkContent {
    data class Text(
        val text: String,
    ) : NewWorkContent

    data class Image(
        val attachment: AttachmentDraft,
    ) : NewWorkContent

    data class Table(
        val content: TableContent,
    ) : NewWorkContent
}

data class CreatedWorkContent(
    val entry: WorkEntry,
    val block: ContentBlock,
)
