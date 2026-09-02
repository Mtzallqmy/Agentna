package com.mtzallqmy.agentna.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 19.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 15.sp, lineHeight = 23.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 10.sp, lineHeight = 14.sp)
)
