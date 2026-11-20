package com.snhrnck.mintlist.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val isDark: Boolean,
    val bgColor: Color,
    val cardColor: Color,
    val defaultBorder: Color,
    val checkedBorder: Color,
    val accentColor: Color,
    val textColor: Color,
    val swipeDeleteColor: Color
)

val DarkAppColors = AppColors(
    isDark = true,
    bgColor = Color(0xFF121513),
    cardColor = Color(0xFF1C211E),
    defaultBorder = Color(0xFF2D3530),
    checkedBorder = Color(0xFF52B788),
    accentColor = Color(0xFF74C69D),
    textColor = Color(0xFFE6EBE8),
    swipeDeleteColor = Color(0xFFB84A3E)
)

val LightAppColors = AppColors(
    isDark = false,
    bgColor = Color(0xFFF6F5F0),
    cardColor = Color(0xFFFFFFFF),
    defaultBorder = Color(0xFFDCE0D9),
    checkedBorder = Color(0xFF2E7D5B),
    accentColor = Color(0xFF3A5A40),
    textColor = Color(0xFF1D201E),
    swipeDeleteColor = Color(0xFFD96B5C)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }