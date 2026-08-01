package com.worklogai.app.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.worklogai.app.R
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val SETTINGS_ROUTE = "settings"

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelResourceId: Int,
    val icon: ImageVector,
) {
    TODAY(
        route = "today",
        labelResourceId = R.string.nav_today,
        icon = Icons.Outlined.Today,
    ),
    HISTORY(
        route = "history",
        labelResourceId = R.string.nav_history,
        icon = Icons.Outlined.History,
    ),
    SUMMARY(
        route = "summary",
        labelResourceId = R.string.nav_summary,
        icon = Icons.Outlined.AutoAwesome,
    ),
    SETTINGS(
        route = SETTINGS_ROUTE,
        labelResourceId = R.string.settings,
        icon = Icons.Outlined.Settings,
    ),
}

const val DATA_MANAGEMENT_ROUTE = "data-management"
const val ENTRY_DATE_ARGUMENT = "entryDate"
const val LINKED_CONTENT_BLOCK_ARGUMENT = "linkedContentBlockId"
const val ENTRY_EDITOR_ROUTE =
    "entry/{$ENTRY_DATE_ARGUMENT}?$LINKED_CONTENT_BLOCK_ARGUMENT={$LINKED_CONTENT_BLOCK_ARGUMENT}"

fun entryEditorRoute(
    entryDate: String,
    linkedContentBlockId: String? = null,
): String =
    buildString {
        append("entry/")
        append(entryDate)
        linkedContentBlockId?.let {
            append("?$LINKED_CONTENT_BLOCK_ARGUMENT=")
            append(URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20"))
        }
    }
