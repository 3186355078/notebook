package com.worklogai.app.app.navigation

import android.content.Intent
import com.worklogai.app.core.model.SummaryType
import java.time.LocalDate

data class SummaryNavigationTarget(
    val summaryType: SummaryType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
)

const val SUMMARY_TYPE_ARGUMENT = "summaryType"
const val SUMMARY_PERIOD_START_ARGUMENT = "periodStart"
const val SUMMARY_PERIOD_END_ARGUMENT = "periodEnd"
const val SUMMARY_PERIOD_ROUTE =
    "summary/{$SUMMARY_TYPE_ARGUMENT}/{$SUMMARY_PERIOD_START_ARGUMENT}/{$SUMMARY_PERIOD_END_ARGUMENT}"
const val EXTRA_SUMMARY_TYPE = "com.worklogai.app.extra.SUMMARY_TYPE"
const val EXTRA_SUMMARY_PERIOD_START = "com.worklogai.app.extra.SUMMARY_PERIOD_START"
const val EXTRA_SUMMARY_PERIOD_END = "com.worklogai.app.extra.SUMMARY_PERIOD_END"

fun summaryPeriodRoute(target: SummaryNavigationTarget): String =
    "summary/${target.summaryType.name}/${target.periodStart}/${target.periodEnd}"

fun Intent.summaryNavigationTargetOrNull(): SummaryNavigationTarget? =
    summaryNavigationTargetOrNull(
        getStringExtra(EXTRA_SUMMARY_TYPE),
        getStringExtra(EXTRA_SUMMARY_PERIOD_START),
        getStringExtra(EXTRA_SUMMARY_PERIOD_END),
    )

internal fun summaryNavigationTargetOrNull(
    typeValue: String?,
    startValue: String?,
    endValue: String?,
): SummaryNavigationTarget? {
    val type = typeValue?.let(::parseSummaryType)
    val start = startValue?.let(::parseDate)
    val end = endValue?.let(::parseDate)
    return if (type != null && start != null && end != null) {
        SummaryNavigationTarget(type, start, end).takeIf(SummaryNavigationTarget::hasCanonicalPeriod)
    } else {
        null
    }
}

internal fun SummaryNavigationTarget.hasCanonicalPeriod(): Boolean =
    when (summaryType) {
        SummaryType.WEEKLY ->
            periodStart.dayOfWeek == java.time.DayOfWeek.MONDAY &&
                periodEnd == periodStart.plusDays((WEEKLY_PERIOD_LENGTH - 1).toLong())
        SummaryType.MONTHLY ->
            periodStart.dayOfMonth == 1 &&
                periodEnd ==
                java.time.YearMonth
                    .from(periodStart)
                    .atEndOfMonth()
    }

private fun parseSummaryType(value: String): SummaryType? = runCatching { SummaryType.valueOf(value) }.getOrNull()

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private const val WEEKLY_PERIOD_LENGTH = 7
