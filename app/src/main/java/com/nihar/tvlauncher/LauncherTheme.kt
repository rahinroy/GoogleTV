package com.nihar.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Dark, TV-friendly theme. Design is intentionally minimal for now. */
@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            surface = Color(0xFF0E0F13),
            onSurface = Color(0xFFECECEC),
            primary = Color(0xFF8AB4F8),
        ),
        content = content,
    )
}
