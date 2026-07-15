package com.worklogai.app.core.database.converter

import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RoomTypeConvertersTest {
    private val converters = RoomTypeConverters()

    @Test
    fun `converts LocalDate and Instant without losing precision`() {
        val date = LocalDate.of(2026, 7, 12)
        val instant = Instant.ofEpochMilli(1_784_000_123_456)

        assertEquals(date, converters.epochDayToLocalDate(converters.localDateToEpochDay(date)))
        assertEquals(instant, converters.epochMillisToInstant(converters.instantToEpochMillis(instant)))
        assertNull(converters.epochDayToLocalDate(null))
        assertNull(converters.epochMillisToInstant(null))
    }

    @Test
    fun `converts enum values and rejects unknown database values`() {
        assertEquals(
            ContentBlockType.TABLE,
            converters.stringToContentBlockType(converters.contentBlockTypeToString(ContentBlockType.TABLE)),
        )
        assertEquals(
            SummaryType.MONTHLY,
            converters.stringToSummaryType(converters.summaryTypeToString(SummaryType.MONTHLY)),
        )
        assertEquals(
            SummaryStatus.FAILED,
            converters.stringToSummaryStatus(converters.summaryStatusToString(SummaryStatus.FAILED)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToContentBlockType("UNKNOWN")
        }
    }
}
