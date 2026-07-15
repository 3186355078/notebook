package com.worklogai.app.core.history

import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.WorkEntry
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

fun interface WorkEntryVisibilityPolicy {
    fun isVisible(entry: WorkEntry): Boolean
}

class DefaultWorkEntryVisibilityPolicy
    @Inject
    constructor() : WorkEntryVisibilityPolicy {
        override fun isVisible(entry: WorkEntry): Boolean {
            if (entry.isDeleted) return false
            return entry.title.notBlankOrNull() != null || entry.blocks.any(ContentBlock::isMeaningful)
        }
    }

fun interface WorkEntrySummaryBuilder {
    fun build(entry: WorkEntry): WorkEntrySummary?
}

class DefaultWorkEntrySummaryBuilder
    @Inject
    constructor(
        private val visibilityPolicy: WorkEntryVisibilityPolicy,
    ) : WorkEntrySummaryBuilder {
        override fun build(entry: WorkEntry): WorkEntrySummary? {
            if (!visibilityPolicy.isVisible(entry)) return null

            val sortedBlocks = entry.blocks.sortedBy(ContentBlock::order)
            val textBlocks = sortedBlocks.filterIsInstance<ContentBlock.Text>()
            val imageCount = sortedBlocks.count { it is ContentBlock.Image }
            val tableBlocks = sortedBlocks.filterIsInstance<ContentBlock.Table>()
            val firstText = textBlocks.firstOrNull { it.content.isNotBlank() }?.content?.toPreview()
            val firstTableTitle = tableBlocks.firstNotNullOfOrNull { it.content.title.notBlankOrNull() }
            val preview =
                firstText
                    ?: fallbackPreview(
                        title = entry.title.notBlankOrNull(),
                        imageCount = imageCount,
                        tableCount = tableBlocks.size,
                        firstTableTitle = firstTableTitle,
                    )

            return WorkEntrySummary(
                entryId = entry.id,
                date = entry.entryDate,
                previewText = preview,
                textBlockCount = textBlocks.count { it.content.isNotBlank() },
                imageCount = imageCount,
                tableCount = tableBlocks.size,
                updatedAt = entry.updatedAt,
            )
        }
    }

internal data class HistorySummaryInput(
    val entryId: String,
    val date: LocalDate,
    val title: String?,
    val updatedAt: Instant,
    val textContents: List<String>,
    val imageCount: Int,
    val tableCount: Int,
    val tableTitles: List<String?>,
)

internal fun buildHistorySummary(input: HistorySummaryInput): WorkEntrySummary? {
    val textBlocks = input.textContents.filter(String::isNotBlank)
    val normalizedTitle = input.title.notBlankOrNull()
    val firstTableTitle = input.tableTitles.firstNotNullOfOrNull { it.notBlankOrNull() }
    if (!hasVisibleHistoryContent(normalizedTitle, textBlocks, input.imageCount, input.tableCount)) return null

    return WorkEntrySummary(
        entryId = input.entryId,
        date = input.date,
        previewText =
            textBlocks.firstOrNull()?.toPreview()
                ?: fallbackPreview(normalizedTitle, input.imageCount, input.tableCount, firstTableTitle),
        textBlockCount = textBlocks.size,
        imageCount = input.imageCount,
        tableCount = input.tableCount,
        updatedAt = input.updatedAt,
    )
}

internal fun String.toPreview(maxCodePoints: Int = 100): String {
    val compact = trim().replace(WHITESPACE, " ")
    if (compact.codePointCount(0, compact.length) <= maxCodePoints) return compact
    val endIndex = compact.offsetByCodePoints(0, maxCodePoints)
    return compact.substring(0, endIndex) + "…"
}

private fun fallbackPreview(
    title: String?,
    imageCount: Int,
    tableCount: Int,
    firstTableTitle: String?,
): String =
    when {
        imageCount > 0 && tableCount > 0 -> "包含图片和表格"
        imageCount > 0 -> "图片记录"
        tableCount > 0 -> firstTableTitle ?: "表格记录"
        else -> title.orEmpty()
    }

private fun String?.notBlankOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun ContentBlock.isMeaningful(): Boolean =
    when (this) {
        is ContentBlock.Text -> content.isNotBlank()
        is ContentBlock.Image,
        is ContentBlock.Table,
        -> true
    }

private fun hasVisibleHistoryContent(
    title: String?,
    textBlocks: List<String>,
    imageCount: Int,
    tableCount: Int,
): Boolean = title != null || textBlocks.isNotEmpty() || imageCount > 0 || tableCount > 0

private val WHITESPACE = Regex("\\s+")
