package com.roomcheck.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RC {
    val bg = Color(0xFFF2F2F7)
    val card = Color(0xFFFFFFFF)
    val sep = Color(0xFFE4E4E9)
    val text = Color(0xFF111114)
    val sub = Color(0xFF8A8A8E)
    val sub2 = Color(0xFF63656B)
    val blue = Color(0xFF0A63C4)
    val blueL = Color(0xFFEAF1FB)
    val green = Color(0xFF12795A)
    val greenL = Color(0xFFE6F4EE)
    val red = Color(0xFFC42B21)
    val redL = Color(0xFFFBEBEA)
    val grey = Color(0xFF8A8A8E)
    val greyL = Color(0xFFEEEEF1)
    val wall = Color(0xFF24262D)
    val floor = Color(0xFFFBFBFA)
    val swing = Color(0xFFBFC1C7)
    val bunk = Color(0xFFB7B9C2)   // the faint "top"/"bottom" label on a bunk
}

// The container roles matter as soon as a stock Material control is used - the time picker paints
// its selected hour from primaryContainer and its AM/PM from tertiaryContainer, which left
// unset are Material's own purple and pink and look like a different app bolted on.
private val scheme = lightColorScheme(
    primary = RC.blue,
    background = RC.bg,
    surface = RC.card,
    onBackground = RC.text,
    onSurface = RC.text,
    primaryContainer = RC.blueL,
    onPrimaryContainer = RC.blue,
    tertiaryContainer = RC.blueL,
    onTertiaryContainer = RC.blue,
    secondaryContainer = RC.greyL,
    onSecondaryContainer = RC.text,
    surfaceVariant = RC.greyL,
    onSurfaceVariant = RC.sub2,
    outline = RC.sub
)

@Composable
fun RoomCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
