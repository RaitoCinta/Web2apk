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

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldBright,
    secondary = CyanBright,
    onSecondary = Color(0xFF042F3D),
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = CyanBright,
    tertiary = AmberSignal,
    onTertiary = Color(0xFF2D1B02),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFCD34D),
    background = ForgeDarkBg,
    onBackground = TextDarkPrimary,
    surface = ForgeDarkSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = ForgeDarkSurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    outline = ForgeDarkBorder,
    error = ErrorRedDark,
    onError = Color(0xFF3F0C0C),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = CyanAccent,
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = AmberSignal,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF78350F),
    background = ForgeLightBg,
    onBackground = TextLightPrimary,
    surface = ForgeLightSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = ForgeLightSurfaceVariant,
    onSurfaceVariant = TextLightSecondary,
    outline = ForgeLightBorder,
    error = ErrorRedLight,
    onError = Color.White,
  )

private val ForgeShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
  )

@Composable
fun Web2ApkTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = ForgeShapes,
    content = content,
  )
}
