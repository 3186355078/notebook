package com.worklogai.app.app

import com.worklogai.app.app.navigation.ENTRY_EDITOR_ROUTE
import com.worklogai.app.app.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreNavigationTest {
    @Test
    fun restoreCompletionRecreatesTodayDestination() {
        val options = restoreCompletionNavOptions()

        assertEquals(TopLevelDestination.TODAY.route, options.popUpToRoute)
        assertTrue(options.isPopUpToInclusive())
        assertTrue(options.shouldLaunchSingleTop())
    }

    @Test
    fun linkedEntryNavigationUsesSingleTopAndRejectsTheCurrentTarget() {
        assertTrue(entryEditorNavOptions().shouldLaunchSingleTop())
        assertFalse(
            shouldNavigateToEntryEditor(
                currentRoute = ENTRY_EDITOR_ROUTE,
                currentDate = "2026-07-30",
                currentLinkedContentBlockId = "block-1",
                targetDate = "2026-07-30",
                targetLinkedContentBlockId = "block-1",
            ),
        )
        assertTrue(
            shouldNavigateToEntryEditor(
                currentRoute = ENTRY_EDITOR_ROUTE,
                currentDate = "2026-07-30",
                currentLinkedContentBlockId = "block-1",
                targetDate = "2026-07-30",
                targetLinkedContentBlockId = "block-2",
            ),
        )
    }
}
