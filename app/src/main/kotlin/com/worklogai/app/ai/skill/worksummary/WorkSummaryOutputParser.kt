package com.worklogai.app.ai.skill.worksummary

import kotlinx.serialization.decodeFromString
import java.time.LocalDate
import javax.inject.Inject

interface WorkSummaryOutputParser {
    fun parse(
        rawContent: String,
        request: WorkSummarySkillRequest,
    ): Result<WorkSummaryResult>
}

class DefaultWorkSummaryOutputParser
    @Inject
    constructor() : WorkSummaryOutputParser {
        private val json = SkillJson.instance

        override fun parse(
            rawContent: String,
            request: WorkSummarySkillRequest,
        ): Result<WorkSummaryResult> =
            runCatching {
                val rawJson = rawContent.extractJsonObject() ?: throw WorkSummarySkillException()
                val decoded = json.decodeFromString<WorkSummaryResult>(rawJson).sanitize(request)
                decoded.takeIf(WorkSummaryResult::hasSubstantiveContent) ?: throw WorkSummarySkillException()
            }
    }

private fun String.extractJsonObject(): String? {
    val trimmed =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
}

private fun WorkSummaryResult.sanitize(request: WorkSummarySkillRequest): WorkSummaryResult {
    val allowedDates =
        request.sourceDates
            .takeIf { dates -> dates.isNotEmpty() }
            ?.map(LocalDate::toString)
            ?.toSet()
            ?: generateSequence(request.periodStart) { date ->
                if (date <
                    request.periodEnd
                ) {
                    date.plusDays(1)
                } else {
                    null
                }
            }.map(LocalDate::toString)
                .toSet()
    return copy(
        title = title.clean(MAX_TITLE_LENGTH),
        overview = overview.clean(MAX_OVERVIEW_LENGTH),
        completedItems = completedItems.sanitizeItems(allowedDates),
        inProgressItems = inProgressItems.sanitizeItems(allowedDates),
        problemsAndSolutions = problemsAndSolutions.sanitizeProblems(allowedDates),
        keyDecisions = keyDecisions.sanitizeItems(allowedDates),
        metrics = metrics.sanitizeMetrics(allowedDates),
        unfinishedItems = unfinishedItems.sanitizeItems(allowedDates),
        nextActions = nextActions.sanitizeItems(allowedDates),
        risks = risks.sanitizeItems(allowedDates),
        highlights = highlights.sanitizeItems(allowedDates),
    )
}

private fun WorkSummaryResult.hasSubstantiveContent(): Boolean =
    overview.isNotBlank() ||
        completedItems.isNotEmpty() ||
        inProgressItems.isNotEmpty() ||
        problemsAndSolutions.isNotEmpty() ||
        keyDecisions.isNotEmpty() ||
        metrics.isNotEmpty() ||
        unfinishedItems.isNotEmpty() ||
        nextActions.isNotEmpty() ||
        risks.isNotEmpty() ||
        highlights.isNotEmpty()

private fun List<SummaryItem>.sanitizeItems(allowedDates: Set<String>): List<SummaryItem> =
    asSequence()
        .map { item ->
            item.copy(
                content = item.content.clean(MAX_ITEM_LENGTH),
                sourceDates = item.sourceDates.validDates(allowedDates),
            )
        }.filter { item -> item.content.isNotEmpty() }
        .distinctBy { item -> item.content to item.sourceDates }
        .take(MAX_ITEMS_PER_SECTION)
        .toList()

private fun List<ProblemSolutionItem>.sanitizeProblems(allowedDates: Set<String>): List<ProblemSolutionItem> =
    asSequence()
        .map { item ->
            item.copy(
                problem = item.problem.clean(MAX_ITEM_LENGTH),
                solution = item.solution?.clean(MAX_ITEM_LENGTH)?.takeIf(String::isNotEmpty),
                sourceDates = item.sourceDates.validDates(allowedDates),
            )
        }.filter { item -> item.problem.isNotEmpty() }
        .distinctBy { item -> Triple(item.problem, item.solution, item.sourceDates) }
        .take(MAX_ITEMS_PER_SECTION)
        .toList()

private fun List<MetricItem>.sanitizeMetrics(allowedDates: Set<String>): List<MetricItem> =
    asSequence()
        .map { item ->
            item.copy(
                name = item.name.clean(MAX_ITEM_LENGTH),
                value = item.value.clean(MAX_ITEM_LENGTH),
                context = item.context?.clean(MAX_ITEM_LENGTH)?.takeIf(String::isNotEmpty),
                sourceDates = item.sourceDates.validDates(allowedDates),
            )
        }.filter { item -> item.name.isNotEmpty() && item.value.isNotEmpty() }
        .distinctBy { item -> listOf(item.name, item.value, item.context, item.sourceDates) }
        .take(MAX_ITEMS_PER_SECTION)
        .toList()

private fun List<String>.validDates(allowedDates: Set<String>): List<String> =
    filter { value -> runCatching { LocalDate.parse(value) }.isSuccess && value in allowedDates }.distinct()

private fun String.clean(limit: Int): String = trim().replace(Regex("\\s+"), " ").takeCodePoints(limit)

private fun String.takeCodePoints(maximum: Int): String =
    if (codePointCount(0, length) <= maximum) this else substring(0, offsetByCodePoints(0, maximum))

private const val MAX_TITLE_LENGTH = 160
private const val MAX_OVERVIEW_LENGTH = 2_000
private const val MAX_ITEM_LENGTH = 1_000
private const val MAX_ITEMS_PER_SECTION = 30
