package com.mtzallqmy.agentna.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun agentnaTextStyle(
    weight: FontWeight,
    size: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing
)

val Typography = Typography(
    displayLarge = agentnaTextStyle(FontWeight.Bold, 32.sp, 40.sp, (-0.5).sp),
    displayMedium = agentnaTextStyle(FontWeight.Bold, 28.sp, 36.sp, (-0.5).sp),
    displaySmall = agentnaTextStyle(FontWeight.SemiBold, 24.sp, 32.sp),
    headlineMedium = agentnaTextStyle(FontWeight.SemiBold, 20.sp, 28.sp),
    titleLarge = agentnaTextStyle(FontWeight.Bold, 19.sp, 26.sp),
    titleMedium = agentnaTextStyle(FontWeight.SemiBold, 16.sp, 24.sp),
    titleSmall = agentnaTextStyle(FontWeight.Medium, 14.sp, 20.sp),
    bodyLarge = agentnaTextStyle(FontWeight.Normal, 15.sp, 23.sp, 0.2.sp),
    bodyMedium = agentnaTextStyle(FontWeight.Normal, 13.sp, 20.sp),
    bodySmall = agentnaTextStyle(FontWeight.Normal, 12.sp, 17.sp),
    labelLarge = agentnaTextStyle(FontWeight.SemiBold, 14.sp, 20.sp),
    labelMedium = agentnaTextStyle(FontWeight.Medium, 12.sp, 16.sp),
    labelSmall = agentnaTextStyle(FontWeight.Medium, 10.sp, 14.sp)
)
