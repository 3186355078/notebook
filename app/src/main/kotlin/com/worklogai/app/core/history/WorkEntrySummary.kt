package com.worklogai.app.core.history

import java.time.Instant
import java.time.LocalDate

/** A compact, UI-safe representation of an entry for the history list. */
data class WorkEntrySummary(
    val entryId: String,
    val date: LocalDate,
    val previewText: String,
    val textBlockCount: Int,
    val imageCount: Int,
    val tableCount: Int,
    val updatedAt: Instant,
) {
    val contentSummary: String
        get() =
            listOfNotNull(
                textBlockCount.takeIf { it > 0 }?.let { "$it 条文字" },
                imageCount.takeIf { it > 0 }?.let { "$it 张图片" },
                tableCount.takeIf { it > 0 }?.let { "$it 个表格" },
            ).joinToString(" · ")
}

data class WorkHistoryPage(
    val entries: List<WorkEntrySummary>,
    val nextOffset: Int?,
)
