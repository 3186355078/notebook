package com.worklogai.app.core.designsystem.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing

@Composable
fun WorkLogMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val document = remember(markdown) { MarkdownParser.parse(markdown) }
    SelectionContainer {
        Column(modifier = modifier.fillMaxWidth()) {
            document.blocks.forEach { block -> MarkdownBlockContent(block) }
        }
    }
}

@Composable
private fun MarkdownBlockContent(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> MarkdownHeading(block)
        is MarkdownBlock.Paragraph ->
            MarkdownText(
                content = block.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = WorkLogSpacing.medium),
            )
        is MarkdownBlock.ListBlock -> MarkdownList(block)
        is MarkdownBlock.Quote -> MarkdownQuote(block)
        is MarkdownBlock.CodeBlock -> MarkdownCodeBlock(block)
        MarkdownBlock.Divider ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = WorkLogSpacing.large),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
    }
}

@Composable
private fun MarkdownHeading(block: MarkdownBlock.Heading) {
    val style =
        when (block.level) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            TERTIARY_HEADING_LEVEL -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            else -> MaterialTheme.typography.titleSmall
        }
    val top = if (block.level <= SECONDARY_HEADING_LEVEL) WorkLogSpacing.extraLarge else WorkLogSpacing.large
    val bottom = if (block.level == 1) WorkLogSpacing.large else WorkLogSpacing.small
    MarkdownText(
        content = block.content,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .padding(top = top, bottom = bottom)
                .semantics { heading() },
    )
}

@Composable
private fun MarkdownList(block: MarkdownBlock.ListBlock) {
    Column(
        modifier = Modifier.padding(bottom = WorkLogSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.extraSmall),
    ) {
        block.items.forEach { item ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = WorkLogSpacing.large * item.depth),
                horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small),
                verticalAlignment = Alignment.Top,
            ) {
                when (item.checked) {
                    true ->
                        Icon(
                            imageVector = Icons.Outlined.CheckBox,
                            contentDescription = "已完成",
                            modifier = Modifier.size(WorkLogSpacing.largePlus),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    false ->
                        Icon(
                            imageVector = Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = "未完成",
                            modifier = Modifier.size(WorkLogSpacing.largePlus),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    null ->
                        Text(
                            text = item.ordinal?.let { "$it." } ?: "•",
                            modifier = Modifier.width(WorkLogSpacing.extraLarge),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                }
                MarkdownText(
                    content = item.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MarkdownQuote(block: MarkdownBlock.Quote) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = WorkLogSpacing.small),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(WorkLogSpacing.extraSmall)
                        .background(MaterialTheme.colorScheme.primary),
            )
            MarkdownText(
                content = block.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(WorkLogSpacing.medium),
            )
        }
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock.CodeBlock) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = WorkLogSpacing.small),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(WorkLogSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.small),
        ) {
            block.language?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = block.code,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MarkdownText(
    content: List<MarkdownInline>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val linkColor = MaterialTheme.colorScheme.primary
    Text(
        text =
            remember(content, inlineCodeBackground, linkColor) {
                content.toAnnotatedString(inlineCodeBackground, linkColor)
            },
        modifier = modifier,
        color = color,
        style = style,
    )
}

private fun List<MarkdownInline>.toAnnotatedString(
    inlineCodeBackground: Color,
    linkColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        fun appendInline(inline: MarkdownInline) {
            when (inline) {
                is MarkdownInline.Text -> append(inline.value)
                is MarkdownInline.Strong ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        inline.children.forEach(::appendInline)
                    }
                is MarkdownInline.Emphasis ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        inline.children.forEach(::appendInline)
                    }
                is MarkdownInline.InlineCode ->
                    withStyle(
                        SpanStyle(
                            background = inlineCodeBackground,
                            fontFamily = FontFamily.Monospace,
                        ),
                    ) {
                        append(" ${inline.value} ")
                    }
                is MarkdownInline.Link ->
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        inline.children.forEach(::appendInline)
                    }
                is MarkdownInline.ImageAlternative ->
                    withStyle(SpanStyle(color = linkColor, fontStyle = FontStyle.Italic)) {
                        append("[图片：${inline.alternative.ifBlank { "未命名" }}]")
                    }
            }
        }
        this@toAnnotatedString.forEach(::appendInline)
    }

private const val SECONDARY_HEADING_LEVEL = 2
private const val TERTIARY_HEADING_LEVEL = 3
