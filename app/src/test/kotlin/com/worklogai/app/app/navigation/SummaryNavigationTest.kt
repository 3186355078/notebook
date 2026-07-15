package com.worklogai.app.app.navigation

import com.worklogai.app.core.model.SummaryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SummaryNavigationTest {
    @Test
    fun `canonical weekly and monthly targets parse to stable routes`() {
        val weekly = summaryNavigationTargetOrNull("WEEKLY", "2026-07-13", "2026-07-19")
        val monthly = summaryNavigationTargetOrNull("MONTHLY", "2028-02-01", "2028-02-29")

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
}
