package com.worklogai.app.app

import com.worklogai.app.app.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
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
}
