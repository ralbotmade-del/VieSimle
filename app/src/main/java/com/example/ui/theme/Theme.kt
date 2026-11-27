package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VieSimpleLightColors = lightColorScheme(
    primary = ForestSagePrimary,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = DeepSageText,
    secondary = EucalyptusSecondary,
    onSecondary = Color.White,
    secondaryContainer = EucalyptusContainer,
    onSecondaryContainer = DeepSageText,
    tertiary = TerracottaTertiary,
    onTertiary = Color.White,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = Color(0xFF410E03),
    background = CreamBackground,
    onBackground = WarmInk,
    surface = WarmWhiteSurface,
    onSurface = WarmInk,
    surfaceVariant = WarmSandSurfaceVariant,
    onSurfaceVariant = MutedSlate,
    outline = WarmOutline,
    outlineVariant = SubtleOutline,
    error = UrgentRed,
    onError = Color.White,
    errorContainer = UrgentRedContainer,
    onErrorContainer = Color(0xFF410002)
)

private val VieSimpleDarkColors = darkColorScheme(
    primary = LightMintPrimary,
    onPrimary = DarkForestOnPrimary,
    primaryContainer = DarkSageContainer,
    onPrimaryContainer = LightSageText,
    secondary = SoftEucalyptus,
    onSecondary = DarkForestOnPrimary,
    secondaryContainer = Color(0xFF283D35),
    onSecondaryContainer = LightSageText,
    tertiary = SoftTerracotta,
    onTertiary = Color(0xFF4A180B),
    tertiaryContainer = Color(0xFF5D291C),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = DarkSageBackground,
    onBackground = DarkInkText,
    surface = DarkSageSurface,
    onSurface = DarkInkText,
    surfaceVariant = DarkSageSurfaceVariant,
    onSurfaceVariant = DarkMutedSlate,
    outline = DarkOutline,
    outlineVariant = Color(0xFF2C3632),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val VieSimpleShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VieSimpleTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (darkTheme) VieSimpleDarkColors else VieSimpleLightColors

    MaterialTheme(
        colorScheme = colors,
        typography = VieSimpleTypography,
        shapes = VieSimpleShapes,
        content = content
    )
}
