package com.spacegoose.carlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spacegoose.carlauncher.config.ServerConfig
import com.spacegoose.carlauncher.config.ThemeDefinition
import com.spacegoose.carlauncher.settings.LauncherSettings
import com.spacegoose.carlauncher.settings.ThemeMode

/** Text sized for a glance from the driver's seat. */
private val CarTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontSize = 88.sp, fontWeight = FontWeight.Light),
        displayMedium = t.displayMedium.copy(fontSize = 64.sp, fontWeight = FontWeight.Light),
        headlineMedium = t.headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium),
        titleLarge = t.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = 21.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 19.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 17.sp),
        labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    )
}

/** [serverTheme], when given, is a theme from the config server and replaces the built-in colours. */
@Composable
fun CarLauncherTheme(settings: LauncherSettings, serverTheme: ThemeDefinition? = null, content: @Composable () -> Unit) {
    if (serverTheme != null) {
        MaterialTheme(colorScheme = serverTheme.colorScheme(), typography = CarTypography, content = content)
        return
    }
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = Color(settings.accent.argb)
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = accent,
            background = Color(0xFF0B0E14),
            onBackground = Color(0xFFECEFF4),
            surface = Color(0xFF151A23),
            onSurface = Color(0xFFECEFF4),
            surfaceVariant = Color(0xFF1F2632),
            onSurfaceVariant = Color(0xFFA9B1C0),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = accent,
            background = Color(0xFFEFF2F7),
            onBackground = Color(0xFF12161D),
            surface = Color.White,
            onSurface = Color(0xFF12161D),
            surfaceVariant = Color(0xFFE2E7EF),
            onSurfaceVariant = Color(0xFF4A5363),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = CarTypography, content = content)
}

private fun ThemeDefinition.colorScheme() = with(colors) {
    fun c(hex: String) = Color(ServerConfig.parseColor(hex) ?: 0xFFFF00FF)
    val accent = c(this.accent)
    if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = c(onAccent), secondary = accent, onSecondary = c(onAccent),
            background = c(background), onBackground = c(text),
            surface = c(surface), onSurface = c(text),
            surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(textSecondary),
            surfaceContainer = c(surface), surfaceContainerHigh = c(surfaceVariant), surfaceContainerHighest = c(surfaceVariant),
            secondaryContainer = c(surfaceVariant), onSecondaryContainer = c(text),
            outline = c(textSecondary),
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = c(onAccent), secondary = accent, onSecondary = c(onAccent),
            background = c(background), onBackground = c(text),
            surface = c(surface), onSurface = c(text),
            surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(textSecondary),
            surfaceContainer = c(surface), surfaceContainerHigh = c(surfaceVariant), surfaceContainerHighest = c(surfaceVariant),
            secondaryContainer = c(surfaceVariant), onSecondaryContainer = c(text),
            outline = c(textSecondary),
        )
    }
}
