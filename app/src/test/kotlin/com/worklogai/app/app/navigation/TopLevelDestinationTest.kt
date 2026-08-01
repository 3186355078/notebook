package com.worklogai.app.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `top level routes are stable and unique`() {
        val routes = TopLevelDestination.entries.map(TopLevelDestination::route)

        assertEquals(listOf("today", "history", "summary", "settings"), routes)
        assertEquals(routes.size, routes.toSet().size)
        assertTrue(SETTINGS_ROUTE in routes)
    }

    @Test
    fun `entry route includes an optional linked block without exposing record content`() {
        assertEquals("entry/2026-07-30", entryEditorRoute("2026-07-30"))
        assertEquals(
            "entry/2026-07-30?linkedContentBlockId=block-1",
            entryEditorRoute("2026-07-30", "block-1"),
        )
    }
}
