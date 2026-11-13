package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val NovaDarkChatColorScheme = darkColorScheme(
    primary = NovaOrangeCore,
    onPrimary = Color.White,
    primaryContainer = NovaSurfaceElevated,
    onPrimaryContainer = NovaOrangeBright,
    secondary = NovaOrangeBright,
    onSecondary = NovaBlack,
    tertiary = NovaNeonViolet,
    background = NovaBlack,
    onBackground = NovaTextPrimaryDark,
    surface = NovaCharcoal,
    onSurface = NovaTextPrimaryDark,
    surfaceVariant = NovaSurfaceDark,
    onSurfaceVariant = NovaTextSecondaryDark,
    outline = NovaBorderDark,
    error = Color(0xFFFF6B6B)
)

private val NovaLightVoiceColorScheme = lightColorScheme(
    primary = VoiceSoftPurple,
    onPrimary = Color.White,
    primaryContainer = VoiceSurfaceSoft,
    onPrimaryContainer = VoiceTextPrimary,
    secondary = VoicePinkAccent,
    onSecondary = Color.White,
    tertiary = VoicePeriwinkle,
    background = VoiceWhite,
    onBackground = VoiceTextPrimary,
    surface = VoiceOffWhite,
    onSurface = VoiceTextPrimary,
    surfaceVariant = VoiceSurfaceSoft,
    onSurfaceVariant = VoiceTextSecondary,
    outline = VoiceBorderLight,
    error = Color(0xFFDC2626)
)

val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NovaTheme(
    isVoiceLightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isVoiceLightMode) NovaLightVoiceColorScheme else NovaDarkChatColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = isVoiceLightMode
                insetsController.isAppearanceLightNavigationBars = isVoiceLightMode
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NovaTheme(isVoiceLightMode = !darkTheme, content = content)
}
