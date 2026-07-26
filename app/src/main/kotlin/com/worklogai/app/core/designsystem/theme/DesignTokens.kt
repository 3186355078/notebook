package com.worklogai.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object WorkLogSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val largePlus = 20.dp
    val extraLarge = 24.dp
    val huge = 32.dp
}

object WorkLogElevation {
    val flat = 0.dp
    val raised = 2.dp
    val dragging = 6.dp
    val overlay = 8.dp
}

object WorkLogMotion {
    const val QUICK_MILLIS = 150
    const val STANDARD_MILLIS = 220
    const val EMPHASIZED_MILLIS = 300
}

val WorkLogShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

val WorkLogBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

val WorkLogTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineSmall =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 25.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            ),
        titleSmall =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 25.sp,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
        bodySmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
        labelLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
    )
