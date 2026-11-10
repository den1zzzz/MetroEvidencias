package com.metroevidencias.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF142D3D)
val Teal = Color(0xFF007D72)
val Muted = Color(0xFF596B76)
private val colors = lightColorScheme(
    primary = Teal, onPrimary = Color.White, primaryContainer = Color(0xFFD9F3EC),
    onPrimaryContainer = Color(0xFF004D44), secondary = Ink,
    secondaryContainer = Color(0xFFD9F3EC), onSecondaryContainer = Color(0xFF004D44),
    background = Color(0xFFF3F6F8), surface = Color.White,
    onBackground = Ink, onSurface = Ink, onSurfaceVariant = Muted,
    outline = Color(0xFF81939D), surfaceVariant = Color(0xFFE8EEF1)
)
@Composable
fun MetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp)), content = content)
}
