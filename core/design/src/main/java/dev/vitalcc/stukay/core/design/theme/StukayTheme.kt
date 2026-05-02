package dev.vitalcc.stukay.core.design.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = StukayAccent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = StukayAccentContainer,
    secondary = StukaySignal,
    secondaryContainer = StukaySignalContainer,
    tertiary = StukayStatusOk,
    background = androidx.compose.ui.graphics.Color(0xFFF1F5F4),
    surface = androidx.compose.ui.graphics.Color(0xFFFCFFFE),
    surfaceVariant = StukayThreadSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7CD5BE),
    primaryContainer = Color(0xFF114D40),
    secondary = Color(0xFFFFB57D),
    secondaryContainer = Color(0xFF6A3B09),
    tertiary = Color(0xFF78D8A0),
    background = Color(0xFF0B1513),
    surface = Color(0xFF101817),
    surfaceVariant = StukayThreadSurfaceDark,
)

@Composable
fun StukayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = StukayShapes,
        typography = StukayTypography,
        content = content,
    )
}
