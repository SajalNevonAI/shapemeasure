package com.nevonai.smartshape.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nevonai.smartshape.model.ShapeType

private val Indigo = Color(0xFF5B5BF7)
private val IndigoDeep = Color(0xFF4338CA)
private val Teal = Color(0xFF2DD4BF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B8BFF),
    onPrimary = Color(0xFF0B0B3B),
    primaryContainer = Color(0xFF2E2A85),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Teal,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF0F3D38),
    onSecondaryContainer = Color(0xFFB6F5EA),
    background = Color(0xFF0D0F17),
    onBackground = Color(0xFFE6E7EE),
    surface = Color(0xFF0D0F17),
    onSurface = Color(0xFFE6E7EE),
    surfaceVariant = Color(0xFF1F2230),
    onSurfaceVariant = Color(0xFFA9ADC1),
    surfaceContainerLowest = Color(0xFF090B11),
    surfaceContainerLow = Color(0xFF131623),
    surfaceContainer = Color(0xFF171A28),
    surfaceContainerHigh = Color(0xFF1E2232),
    surfaceContainerHighest = Color(0xFF262A3C),
    outline = Color(0xFF3A3F55),
    outlineVariant = Color(0xFF2A2E40),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = IndigoDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E1FF),
    onPrimaryContainer = Color(0xFF1A1466),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF5EE),
    onSecondaryContainer = Color(0xFF00332D),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF14151C),
    surface = Color(0xFFF7F7FB),
    onSurface = Color(0xFF14151C),
    surfaceVariant = Color(0xFFE6E7F0),
    onSurfaceVariant = Color(0xFF555A70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F1F8),
    surfaceContainer = Color(0xFFECECF4),
    surfaceContainerHigh = Color(0xFFE6E6F0),
    surfaceContainerHighest = Color(0xFFE0E0EC),
    outline = Color(0xFFC3C5D4),
    outlineVariant = Color(0xFFDADBE6),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Measurement figures use tabular digits so values don't jitter while editing. */
val MeasurementValueStyle = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

/** Overlay colours per shape type; chosen to stay legible on camera imagery in both themes. */
fun ShapeType.overlayColor(): Color = when (this) {
    ShapeType.RECTANGLE -> Color(0xFF7C7CFF)
    ShapeType.SQUARE -> Color(0xFFA78BFA)
    ShapeType.CIRCLE -> Teal
    ShapeType.ELLIPSE -> Color(0xFF38BDF8)
    ShapeType.TRIANGLE -> Color(0xFFFBBF24)
    ShapeType.POLYGON -> Color(0xFFF472B6)
    ShapeType.IRREGULAR -> Color(0xFFFB923C)
}

val BrandGradient = listOf(Indigo, Color(0xFF7C3AED))

@Composable
fun SmartShapeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
