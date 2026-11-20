package com.snhrnck.mintlist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ShoppingPlannerTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = if (isDark) {
                darkColorScheme(
                    primary = colors.accentColor,
                    background = colors.bgColor,
                    surface = colors.cardColor,
                    onBackground = colors.textColor,
                    onSurface = colors.textColor,
                    error = colors.swipeDeleteColor
                )
            } else {
                lightColorScheme(
                    primary = colors.accentColor,
                    background = colors.bgColor,
                    surface = colors.cardColor,
                    onBackground = colors.textColor,
                    onSurface = colors.textColor,
                    error = colors.swipeDeleteColor
                )
            },
            content = content
        )
    }
}