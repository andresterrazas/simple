package com.andresterrazas.simple.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andresterrazas.simple.R

val Quicksand = FontFamily(
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_semibold, FontWeight.SemiBold),
    Font(R.font.quicksand_bold, FontWeight.Bold),
)

data class UiStyle(val dark: Boolean) {
    val panel: Color = if (dark) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.17f)
    val panelStrong: Color = if (dark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.24f)
    val scrim: Color = if (dark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.06f)
    val focusedFill: Color = Color.White.copy(alpha = 0.94f)
    val focusedText: Color = Color(0xFF1E2230)
    val text: Color = Color.White
    val danger: Color = Color(0xFFFF6B5E)
}

/** Formas compartidas por todos los menús, para que todas las pantallas se vean iguales. */
object Shapes {
    val row = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val block = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    val hero = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val thumb = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
}

val LocalStyle = staticCompositionLocalOf { UiStyle(false) }
val LocalBackdrop = compositionLocalOf<ImageBitmap?> { null }
val LocalRootSize = compositionLocalOf { IntSize.Zero }

fun ts(size: TextUnit = 17.sp, weight: FontWeight = FontWeight.SemiBold, color: Color = Color.White) =
    TextStyle(fontFamily = Quicksand, fontSize = size, fontWeight = weight, color = color)
