package com.worklogai.app.core.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class DateRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(start <= end) { "Date range start must not be after end." }
    }
}

interface WorkPeriodCalculator {
    fun dayContaining(date: LocalDate): DateRange

    fun weekContaining(date: LocalDate): DateRange

    fun monthContaining(month: YearMonth): DateRange
}

class DefaultWorkPeriodCalculator
    @Inject
    constructor() : WorkPeriodCalculator {
        override fun dayContaining(date: LocalDate): DateRange = DateRange(date, date)

        override fun weekContaining(date: LocalDate): DateRange {
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return DateRange(start, start.plusDays(DAYS_PER_WEEK_MINUS_ONE))
        }

        override fun monthContaining(month: YearMonth): DateRange = DateRange(month.atDay(1), month.atEndOfMonth())
    }

private const val DAYS_PER_WEEK_MINUS_ONE = 6L
