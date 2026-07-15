package com.worklogai.app.ai.skill.worksummary

import com.worklogai.app.core.model.SummaryType
import kotlinx.serialization.Serializable

@Serializable
data class WorkSummarySkillInput(
    val summaryType: SummaryType,
    val periodStart: String,
    val periodEnd: String,
    val language: String,
    val entries: List<WorkSummaryEntryInput>,
)

@Serializable
data class WorkSummaryEntryInput(
    val date: String,
    val title: String? = null,
    val contents: List<WorkSummaryContentInput> = emptyList(),
)

@Serializable
data class WorkSummaryContentInput(
    val type: WorkSummaryContentType,
    val text: String? = null,
    val table: WorkSummaryTableInput? = null,
)

@Serializable
enum class WorkSummaryContentType {
    TEXT,
    IMAGE,
    TABLE,
}

@Serializable
data class WorkSummaryTableInput(
    val title: String? = null,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
)

@Serializable
data class WorkSummaryResult(
    val title: String = "",
    val overview: String = "",
    val completedItems: List<SummaryItem> = emptyList(),
    val inProgressItems: List<SummaryItem> = emptyList(),
    val problemsAndSolutions: List<ProblemSolutionItem> = emptyList(),
    val keyDecisions: List<SummaryItem> = emptyList(),
    val metrics: List<MetricItem> = emptyList(),
    val unfinishedItems: List<SummaryItem> = emptyList(),
    val nextActions: List<SummaryItem> = emptyList(),
    val risks: List<SummaryItem> = emptyList(),
    val highlights: List<SummaryItem> = emptyList(),
)

@Serializable
data class SummaryItem(
    val content: String = "",
    val sourceDates: List<String> = emptyList(),
)

@Serializable
data class ProblemSolutionItem(
    val problem: String = "",
    val solution: String? = null,
    val sourceDates: List<String> = emptyList(),
)

@Serializable
data class MetricItem(
    val name: String = "",
    val value: String = "",
    val context: String? = null,
    val sourceDates: List<String> = emptyList(),
)

data class SummaryInputLimitResult(
    val input: WorkSummarySkillInput,
    val wasTruncated: Boolean,
    val excludedContentCount: Int,
)

class WorkSummarySkillException : IllegalArgumentException("大模型返回格式无法解析")
