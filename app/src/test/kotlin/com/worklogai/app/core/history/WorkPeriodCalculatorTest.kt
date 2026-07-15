package com.worklogai.app.core.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WorkPeriodCalculatorTest {
    private val calculator = DefaultWorkPeriodCalculator()

    @Test
    fun `week starts Monday and ends Sunday across month and year`() {
        assertEquals(
            DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)),
            calculator.weekContaining(LocalDate.of(2026, 7, 12)),
        )
        assertEquals(
            DateRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)),
            calculator.weekContaining(LocalDate.of(2027, 1, 1)),
        )
    }

    @Test
    fun `month ranges include leap day and ordinary month end`() {
        assertEquals(
            DateRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)),
            calculator.monthContaining(YearMonth.of(2028, 2)),
        )
        assertEquals(
            DateRange(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)),
            calculator.monthContaining(YearMonth.of(2027, 2)),
        )
    }
}
