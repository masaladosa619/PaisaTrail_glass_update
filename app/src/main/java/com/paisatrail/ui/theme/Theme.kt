package com.paisatrail.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.paisatrail.data.prefs.ThemeMode

/** Semantic colours that stay the same in both themes. */
object AppColors {
    val Positive = Color(0xFF34D17A)
    val PositiveOnLight = Color(0xFF12894A)
    val Expense = Color(0xFFFF4D4F)
    val Warning = Color(0xFFF5B301)
}

private val DarkColors = darkColorScheme(
    primary = AppColors.Positive,
    onPrimary = Color(0xFF04210F),
    primaryContainer = Color(0xFF123822),
    onPrimaryContainer = Color(0xFFB9F5D3),
    secondary = AppColors.Warning,
    onSecondary = Color(0xFF241A00),
    secondaryContainer = Color(0xFF3A2E05),
    onSecondaryContainer = Color(0xFFFFE8A3),
    tertiary = Color(0xFF7C9CFF),
    onTertiary = Color(0xFF07153F),
    error = AppColors.Expense,
    onError = Color(0xFF2B0001),
    errorContainer = Color(0xFF4A1213),
    onErrorContainer = Color(0xFFFFD9D9),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFF9C9CA6),
    outline = Color(0xFF3A3A41),
    outlineVariant = Color(0xFF2A2A2F),
    surfaceContainerLowest = Color(0xFF0B0B0D),
    surfaceContainerLow = Color(0xFF131316),
    surfaceContainer = Color(0xFF17171A),
    surfaceContainerHigh = Color(0xFF1E1E22),
    surfaceContainerHighest = Color(0xFF26262B)
)

private val LightColors = lightColorScheme(
    primary = AppColors.PositiveOnLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF5DF),
    onPrimaryContainer = Color(0xFF05301A),
    secondary = Color(0xFF8A6500),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDB5),
    onSecondaryContainer = Color(0xFF2B2000),
    tertiary = Color(0xFF3558D4),
    onTertiary = Color.White,
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFECECF0),
    onSurfaceVariant = Color(0xFF5E5E68),
    outline = Color(0xFFC4C4CC),
    outlineVariant = Color(0xFFE0E0E6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F1F4),
    surfaceContainerHighest = Color(0xFFE9E9EE)
)

@Composable
fun PaisaTrailTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
