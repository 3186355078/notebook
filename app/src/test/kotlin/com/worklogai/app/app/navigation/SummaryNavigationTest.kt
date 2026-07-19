package com.worklogai.app.app.navigation

import com.worklogai.app.core.model.SummaryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SummaryNavigationTest {
    @Test
    fun `canonical weekly and monthly targets parse to stable routes`() {
        val today = LocalDate.of(2028, 3, 1)
        val weekly = summaryNavigationTargetOrNull("WEEKLY", "2026-07-13", "2026-07-19", today)
        val monthly = summaryNavigationTargetOrNull("MONTHLY", "2028-02-01", "2028-02-29", today)

        assertEquals(
            SummaryNavigationTarget(SummaryType.WEEKLY, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19)),
            weekly,
        )
        assertEquals("summary/MONTHLY/2028-02-01/2028-02-29", summaryPeriodRoute(requireNotNull(monthly)))
    }

    @Test
    fun `invalid type malformed dates and noncanonical ranges are rejected`() {
        assertNull(summaryNavigationTargetOrNull("DAILY", "2026-07-13", "2026-07-19"))
        assertNull(summaryNavigationTargetOrNull("WEEKLY", "not-a-date", "2026-07-19"))
        assertNull(summaryNavigationTargetOrNull("WEEKLY", "2026-07-14", "2026-07-20"))
        assertNull(summaryNavigationTargetOrNull("MONTHLY", "2026-07-01", "2026-07-30"))
    }

    @Test
    fun `current and future periods are rejected`() {
        val today = LocalDate.of(2026, 7, 16)

        assertNull(summaryNavigationTargetOrNull("WEEKLY", "2026-07-13", "2026-07-19", today))
        assertNull(summaryNavigationTargetOrNull("WEEKLY", "2026-07-20", "2026-07-26", today))
        assertNull(summaryNavigationTargetOrNull("MONTHLY", "2026-07-01", "2026-07-31", today))
        assertNull(summaryNavigationTargetOrNull("MONTHLY", "2026-08-01", "2026-08-31", today))
    }
}
