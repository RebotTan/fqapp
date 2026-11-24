package com.fqapp.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 配色直接取自 `lib/widgets/home/home_design.dart` 的 HomePalette，
 * 两个客户端要看起来是同一个 app。
 */
object FqColors {
    val accent = Color(0xFFEF5038)
    val accentStrong = Color(0xFFC73D29)
    val lightBackground = Color(0xFFF6F6F6)
    val darkBackground = Color(0xFF151517)
    val lightSurface = Color(0xFFFFFFFF)
    val darkSurface = Color(0xFF242427)
    val lightText = Color(0xFF262522)
    val darkText = Color(0xFFF5F1EB)
    val lightMuted = Color(0xFF706C66)
    val darkMuted = Color(0xFFA7A39E)
    val lightLine = Color(0xFFE8E4DE)
    val darkLine = Color(0xFF333336)
}

@Composable
fun FqTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = FqColors.accent,
            onPrimary = Color.White,
            background = FqColors.darkBackground,
            onBackground = FqColors.darkText,
            surface = FqColors.darkSurface,
            onSurface = FqColors.darkText,
            onSurfaceVariant = FqColors.darkMuted,
            outline = FqColors.darkLine,
        )
    } else {
        lightColorScheme(
            primary = FqColors.accent,
            onPrimary = Color.White,
            background = FqColors.lightBackground,
            onBackground = FqColors.lightText,
            surface = FqColors.lightSurface,
            onSurface = FqColors.lightText,
            onSurfaceVariant = FqColors.lightMuted,
            outline = FqColors.lightLine,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}