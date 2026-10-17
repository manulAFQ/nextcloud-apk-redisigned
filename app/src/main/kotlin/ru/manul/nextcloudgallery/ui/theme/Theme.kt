package ru.manul.nextcloudgallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val CloudBlue = Color(0xFF3478F6)
val DeepInk = Color(0xFF172238)
val Mist = Color(0xFFF6F8FD)
val SoftBlue = Color(0xFFE9F1FF)
val MutedInk = Color(0xFF71809D)

private val LightColors = lightColorScheme(
    primary = CloudBlue,
    onPrimary = Color.White,
    primaryContainer = SoftBlue,
    onPrimaryContainer = Color(0xFF15438F),
    secondary = Color(0xFF75A6FF),
    background = Mist,
    onBackground = DeepInk,
    surface = Color.White,
    onSurface = DeepInk,
    surfaceVariant = Color(0xFFEFF3FA),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFFD9E1EE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CB8FF),
    onPrimary = Color(0xFF0A326F),
    primaryContainer = Color(0xFF173D78),
    background = Color(0xFF0E1420),
    onBackground = Color(0xFFE9EEFA),
    surface = Color(0xFF171E2B),
    onSurface = Color(0xFFE9EEFA),
    surfaceVariant = Color(0xFF202A3A),
    onSurfaceVariant = Color(0xFFBAC5D9),
    outline = Color(0xFF334057)
)

@Composable
fun CloudGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = CloudTypography,
        content = content
    )
}
