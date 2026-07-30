package com.worklogai.app.app

internal const val WIDE_NAVIGATION_BREAKPOINT_DP = 720f

internal fun usesWideNavigation(widthDp: Float): Boolean = widthDp >= WIDE_NAVIGATION_BREAKPOINT_DP
