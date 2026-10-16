package com.recipe.app.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFFE64A19),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3B0900),
    secondary = Color(0xFF795548),
    onSecondary = Color.White,
    surface = Color(0xFFFFF8F5),
    background = Color(0xFFFFF8F5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5F1900),
    secondary = Color(0xFFE6C7BB),
    surface = Color(0xFF211A17),
    background = Color(0xFF1B1210)
)

@Composable
fun RecipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
