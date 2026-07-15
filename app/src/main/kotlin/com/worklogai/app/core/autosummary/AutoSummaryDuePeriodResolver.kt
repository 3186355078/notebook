package com.worklogai.app.core.autosummary

import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.history.WorkPeriodCalculator
import com.worklogai.app.core.model.SummaryType
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

fun interface AutoSummaryDuePeriodResolver {
    fun resolve(
        today: LocalDate,
        settings: AutoSummarySettings,
        scheduleState: AutoSummaryScheduleState,
    ): List<AutoSummaryPeriod>
}

class DefaultAutoSummaryDuePeriodResolver
    @Inject
    constructor(
        private val workPeriodCalculator: WorkPeriodCalculator,
    ) : AutoSummaryDuePeriodResolver {
        override fun resolve(
            today: LocalDate,
            settings: AutoSummarySettings,
            scheduleState: AutoSummaryScheduleState,
        ): List<AutoSummaryPeriod> {
            val periods =
                buildList {
                    if (settings.weeklyEnabled) {
                        addAll(resolveWeekly(today, scheduleState.lastWeeklyEvaluatedEnd))
                    }
                    if (settings.monthlyEnabled) {
                        addAll(resolveMonthly(today, scheduleState.lastMonthlyEvaluatedEnd))
                    }
                }
            return periods
                .sortedWith(compareBy<AutoSummaryPeriod> { it.period.start }.thenBy { it.summaryType.name })
        }

        private fun resolveWeekly(
            today: LocalDate,
            lastEvaluatedEnd: LocalDate?,
        ): List<AutoSummaryPeriod> {
            val latestCompleted =
                workPeriodCalculator
                    .weekContaining(today)
                    .start
                    .minusWeeks(1)
                    .let(workPeriodCalculator::weekContaining)
            if (lastEvaluatedEnd == null) return listOf(AutoSummaryPeriod(SummaryType.WEEKLY, latestCompleted))
            return weeklyPeriodsAfter(lastEvaluatedEnd, latestCompleted).takeLast(MAX_WEEKLY_CATCH_UP_PERIODS)
        }

        private fun resolveMonthly(
            today: LocalDate,
            lastEvaluatedEnd: LocalDate?,
        ): List<AutoSummaryPeriod> {
            val latestCompleted = workPeriodCalculator.monthContaining(YearMonth.from(today).minusMonths(1))
            if (lastEvaluatedEnd == null) return listOf(AutoSummaryPeriod(SummaryType.MONTHLY, latestCompleted))
            return monthlyPeriodsAfter(lastEvaluatedEnd, latestCompleted).takeLast(MAX_MONTHLY_CATCH_UP_PERIODS)
        }

        private fun weeklyPeriodsAfter(
            lastEvaluatedEnd: LocalDate,
            latestCompleted: DateRange,
        ): List<AutoSummaryPeriod> {
            val periods = mutableListOf<AutoSummaryPeriod>()
            var nextStart = lastEvaluatedEnd.plusDays(1)
            while (nextStart <= latestCompleted.start) {
                val period = workPeriodCalculator.weekContaining(nextStart)
                if (period.end > lastEvaluatedEnd) periods += AutoSummaryPeriod(SummaryType.WEEKLY, period)
                nextStart = nextStart.plusWeeks(1)
            }
            return periods
        }

        private fun monthlyPeriodsAfter(
            lastEvaluatedEnd: LocalDate,
            latestCompleted: DateRange,
        ): List<AutoSummaryPeriod> {
            val periods = mutableListOf<AutoSummaryPeriod>()
            var nextMonth = YearMonth.from(lastEvaluatedEnd).plusMonths(1)
            while (nextMonth.atDay(1) <= latestCompleted.start) {
                periods += AutoSummaryPeriod(SummaryType.MONTHLY, workPeriodCalculator.monthContaining(nextMonth))
                nextMonth = nextMonth.plusMonths(1)
            }
            return periods
        }
    }
