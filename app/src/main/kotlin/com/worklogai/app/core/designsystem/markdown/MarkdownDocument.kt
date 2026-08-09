package com.worklogai.app.core.designsystem.markdown

data class MarkdownDocument(
    val blocks: List<MarkdownBlock>,
) {
    fun toPlainText(): String =
        blocks.joinToString(separator = "\n\n") { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> block.code
                MarkdownBlock.Divider -> "────────"
                is MarkdownBlock.Heading -> block.content.plainText()
                is MarkdownBlock.ListBlock ->
                    block.items.joinToString(separator = "\n") { item ->
                        val indent = "  ".repeat(item.depth)
                        val marker =
                            when {
                                item.checked == true -> "☑"
                                item.checked == false -> "☐"
                                item.ordinal != null -> "${item.ordinal}."
                                else -> "•"
                            }
                        "$indent$marker ${item.content.plainText()}"
                    }
                is MarkdownBlock.Paragraph -> block.content.plainText()
                is MarkdownBlock.Quote -> block.content.plainText()
            }
        }
}

sealed interface MarkdownBlock {
    data class Heading(
        val level: Int,
        val content: List<MarkdownInline>,
    ) : MarkdownBlock

    data class Paragraph(
        val content: List<MarkdownInline>,
    ) : MarkdownBlock

    data class ListBlock(
        val items: List<MarkdownListItem>,
    ) : MarkdownBlock

    data class Quote(
        val content: List<MarkdownInline>,
    ) : MarkdownBlock

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : MarkdownBlock

    data object Divider : MarkdownBlock
}

data class MarkdownListItem(
    val depth: Int,
    val ordinal: Int?,
    val checked: Boolean?,
    val content: List<MarkdownInline>,
)

sealed interface MarkdownInline {
    data class Text(
        val value: String,
    ) : MarkdownInline

    data class Strong(
        val children: List<MarkdownInline>,
    ) : MarkdownInline

    data class Emphasis(
        val children: List<MarkdownInline>,
    ) : MarkdownInline

    data class InlineCode(
        val value: String,
    ) : MarkdownInline

    data class Link(
        val children: List<MarkdownInline>,
        val destination: String,
    ) : MarkdownInline

    data class ImageAlternative(
        val alternative: String,
    ) : MarkdownInline
}

internal fun List<MarkdownInline>.plainText(): String =
    buildString {
        fun appendInline(inline: MarkdownInline) {
            when (inline) {
                is MarkdownInline.Emphasis -> inline.children.forEach(::appendInline)
                is MarkdownInline.ImageAlternative -> append("[图片：${inline.alternative.ifBlank { "未命名" }}]")
                is MarkdownInline.InlineCode -> append(inline.value)
                is MarkdownInline.Link -> inline.children.forEach(::appendInline)
                is MarkdownInline.Strong -> inline.children.forEach(::appendInline)
                is MarkdownInline.Text -> append(inline.value)
            }
        }
        this@plainText.forEach(::appendInline)
    }
