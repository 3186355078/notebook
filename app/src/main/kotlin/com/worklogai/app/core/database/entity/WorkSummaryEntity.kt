package com.worklogai.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "work_summaries",
    indices = [
        Index(value = ["summaryType", "periodStart", "periodEnd"], unique = true),
        Index(value = ["summaryType", "periodStart"]),
    ],
)
data class WorkSummaryEntity(
    @PrimaryKey val id: String,
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
)
