package com.worklogai.app.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `top level routes are stable and unique`() {
        val routes = TopLevelDestination.entries.map(TopLevelDestination::route)

        assertEquals(listOf("today", "history", "summary"), routes)
        assertEquals(routes.size, routes.toSet().size)
        assertTrue(SETTINGS_ROUTE !in routes)
    }
}
