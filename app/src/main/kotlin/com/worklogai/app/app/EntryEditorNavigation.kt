package com.worklogai.app.app

import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import com.worklogai.app.app.navigation.ENTRY_DATE_ARGUMENT
import com.worklogai.app.app.navigation.ENTRY_EDITOR_ROUTE
import com.worklogai.app.app.navigation.LINKED_CONTENT_BLOCK_ARGUMENT
import com.worklogai.app.app.navigation.entryEditorRoute

internal fun entryEditorNavOptions(): NavOptions =
    navOptions {
        launchSingleTop = true
    }

internal fun shouldNavigateToEntryEditor(
    currentRoute: String?,
    currentDate: String?,
    currentLinkedContentBlockId: String?,
    targetDate: String,
    targetLinkedContentBlockId: String?,
): Boolean =
    currentRoute != ENTRY_EDITOR_ROUTE ||
        currentDate != targetDate ||
        currentLinkedContentBlockId != targetLinkedContentBlockId

internal fun NavHostController.navigateToEntryEditor(
    entryDate: String,
    linkedContentBlockId: String? = null,
): Boolean {
    val currentEntry = currentBackStackEntry
    if (
        !shouldNavigateToEntryEditor(
            currentRoute = currentEntry?.destination?.route,
            currentDate = currentEntry?.arguments?.getString(ENTRY_DATE_ARGUMENT),
            currentLinkedContentBlockId = currentEntry?.arguments?.getString(LINKED_CONTENT_BLOCK_ARGUMENT),
            targetDate = entryDate,
            targetLinkedContentBlockId = linkedContentBlockId,
        )
    ) {
        return false
    }
    navigate(
        entryEditorRoute(entryDate, linkedContentBlockId),
        entryEditorNavOptions(),
    )
    return true
}
