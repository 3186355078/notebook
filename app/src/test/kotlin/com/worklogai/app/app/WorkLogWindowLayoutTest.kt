package com.worklogai.app.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkLogWindowLayoutTest {
    @Test
    fun compactWidthsUseBottomNavigation() {
        assertFalse(usesWideNavigation(WIDE_NAVIGATION_BREAKPOINT_DP - 1f))
    }

    @Test
    fun breakpointAndWiderWidthsUseNavigationRail() {
        assertTrue(usesWideNavigation(WIDE_NAVIGATION_BREAKPOINT_DP))
        assertTrue(usesWideNavigation(1_024f))
    }

    @Test
    fun topLevelDestinationsOwnTheirPageHeader() {
        assertFalse(shouldShowTopAppBar(isTopLevelDestination = true))
        assertTrue(shouldShowTopAppBar(isTopLevelDestination = false))
    }
}
