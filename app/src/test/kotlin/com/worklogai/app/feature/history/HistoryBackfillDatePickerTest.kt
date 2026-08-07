package com.worklogai.app.feature.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryBackfillDatePickerTest {
    private val today = LocalDate.of(2026, 8, 7)

    @Test
    fun `past today leap day and cross-year dates are allowed`() {
        assertTrue(isBackfillDateAllowed(today.minusDays(1), today))
        assertTrue(isBackfillDateAllowed(today, today))
        assertTrue(isBackfillDateAllowed(LocalDate.of(2024, 2, 29), today))
        assertTrue(isBackfillDateAllowed(LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun `future date is not allowed`() {
        assertFalse(isBackfillDateAllowed(today.plusDays(1), today))
    }

    @Test
    fun `UTC picker millis converts without timezone drift`() {
        val date = LocalDate.of(2026, 8, 4)
        val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        assertEquals(date, backfillDateFromUtcMillis(millis))
    }
}
