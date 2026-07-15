package com.worklogai.app.core.model

import java.time.Instant
import java.time.LocalDate

enum class SummaryType {
    WEEKLY,
    MONTHLY,
}

enum class SummaryStatus {
    PENDING,
    GENERATING,
    SUCCESS,
    FAILED,
}

data class WorkSummary(
    val id: String,
    val summaryType: SummaryType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val status: SummaryStatus,
    val sourceHash: String?,
    val aiProvider: String?,
    val modelName: String?,
    val originalContent: String?,
    val editedContent: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val generatedAt: Instant?,
) {
    val displayContent: String?
        get() = editedContent ?: originalContent
}

data class GeneratedSummaryContent(
    val summaryType: SummaryType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val sourceHash: String,
    val aiProvider: String,
    val modelName: String,
    val originalContent: String,
    val editedContent: String = "",
    val generatedAt: Instant,
)
