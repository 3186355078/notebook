package com.worklogai.app.core.autosummary

import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.model.SummaryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DefaultAutoSummaryDuePeriodResolverTest {
    private val resolver = DefaultAutoSummaryDuePeriodResolver(DefaultWorkPeriodCalculator())

    @Test
    fun `monday resolves only the prior complete natural week on first enable`() {
        val periods = resolve(LocalDate.of(2026, 7, 20), AutoSummarySettings(weeklyEnabled = true))

        assertEquals(listOf(period(SummaryType.WEEKLY, "2026-07-13", "2026-07-19")), periods)
    }

    @Test
    fun `sunday does not resolve the current unfinished week`() {
        val periods = resolve(LocalDate.of(2026, 7, 19), AutoSummarySettings(weeklyEnabled = true))

        assertEquals(listOf(period(SummaryType.WEEKLY, "2026-07-06", "2026-07-12")), periods)
    }

    @Test
    fun `month start resolves the prior full month including leap year February`() {
        val periods = resolve(LocalDate.of(2028, 3, 1), AutoSummarySettings(monthlyEnabled = true))

        assertEquals(listOf(period(SummaryType.MONTHLY, "2028-02-01", "2028-02-29")), periods)
    }

    @Test
    fun `weekly catch up keeps the most recent four periods in old to new order`() {
        val state = AutoSummaryScheduleState(lastWeeklyEvaluatedEnd = LocalDate.of(2026, 5, 31))

        val periods = resolve(LocalDate.of(2026, 7, 20), AutoSummarySettings(weeklyEnabled = true), state)

        assertEquals(4, periods.size)
        assertEquals(LocalDate.of(2026, 6, 22), periods.first().period.start)
        assertEquals(LocalDate.of(2026, 7, 19), periods.last().period.end)
    }

    @Test
    fun `monthly and weekly catch up each keep their configured bounded candidates`() {
        val state =
            AutoSummaryScheduleState(
                lastWeeklyEvaluatedEnd = LocalDate.of(2026, 5, 31),
                lastMonthlyEvaluatedEnd = LocalDate.of(2026, 2, 28),
            )

        val periods =
            resolve(
                LocalDate.of(2026, 7, 20),
                AutoSummarySettings(weeklyEnabled = true, monthlyEnabled = true),
                state,
            )

        assertEquals(4, periods.count { it.summaryType == SummaryType.WEEKLY })
        assertEquals(2, periods.count { it.summaryType == SummaryType.MONTHLY })
        assertTrue(periods.zipWithNext().all { (first, second) -> first.period.start <= second.period.start })
    }

    @Test
    fun `disabled types return no due periods and repeated input is stable`() {
        val settings = AutoSummarySettings()
        val today = LocalDate.of(2026, 12, 31)

        assertTrue(resolve(today, settings).isEmpty())
        assertEquals(resolve(today, settings), resolve(today, settings))
    }

    private fun resolve(
        today: LocalDate,
        settings: AutoSummarySettings,
        state: AutoSummaryScheduleState = AutoSummaryScheduleState(),
    ): List<AutoSummaryPeriod> = resolver.resolve(today, settings, state)

    private fun period(
        type: SummaryType,
        start: String,
        end: String,
    ) = AutoSummaryPeriod(
        type,
        com.worklogai.app.core.history
            .DateRange(LocalDate.parse(start), LocalDate.parse(end)),
    )
}
