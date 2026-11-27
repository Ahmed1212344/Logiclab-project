package com.legendAhmed.logiclab.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand: violet -> blue
val Violet = Color(0xFF7C3AED)
val Blue = Color(0xFF2563EB)
val Brand = Brush.linearGradient(listOf(Violet, Blue))

// Signals
val LampOn = Color(0xFFFFC83D)
val SegOn = Color(0xFF8B5CF6)
val Danger = Color(0xFFEF4444)

/** Light / Dark palette used by every screen and by the canvas. */
class Pal(val dark: Boolean) {
    val bg = if (dark) Color(0xFF0E0F1E) else Color(0xFFF4F3FF)
    val card = if (dark) Color(0xFF181A33) else Color.White
    val ink = if (dark) Color(0xFFE8E9FF) else Color(0xFF1B1C3A)
    val sub = if (dark) Color(0xFF9A9FC8) else Color(0xFF6B6F99)
    val low = if (dark) Color(0xFF5A5F85) else Color(0xFFA3A8C8)
    val grid = if (dark) Color(0xFF23264A) else Color(0xFFDAD8F5)
}