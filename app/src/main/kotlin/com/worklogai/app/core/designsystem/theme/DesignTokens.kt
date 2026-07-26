package com.worklogai.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp

object WorkLogSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val huge = 32.dp
}

object WorkLogMotion {
    const val QUICK_MILLIS = 150
    const val STANDARD_MILLIS = 220
    const val EMPHASIZED_MILLIS = 300
}

val WorkLogShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    )

val WorkLogTypography = Typography()
