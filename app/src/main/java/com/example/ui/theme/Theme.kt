package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TelemetryColors(
    val channel1: Color,
    val channel2: Color,
    val online: Color,
    val warning: Color,
    val offline: Color,
    val gridLine: Color
)

val LocalTelemetryColors = staticCompositionLocalOf {
    TelemetryColors(
        channel1 = Channel1CyanDark,
        channel2 = Channel2AmberDark,
        online = StatusOnlineDark,
        warning = StatusWarnDark,
        offline = StatusOfflineDark,
        gridLine = SlateOutline
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccentDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryAccentDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryAccentDark,
    background = ObsidianBg,
    onBackground = InkPrimaryDark,
    surface = SlateSurface,
    onSurface = InkPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = InkSecondaryDark,
    surfaceContainerHigh = SlateContainerHigh,
    outline = SlateOutline,
    outlineVariant = SlateOutlineSubtle,
    error = StatusOfflineDark,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccentLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryAccentLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryAccentLight,
    background = AlabasterBg,
    onBackground = InkPrimaryLight,
    surface = CrispSurface,
    onSurface = InkPrimaryLight,
    surfaceVariant = CoolSurfaceVariant,
    onSurfaceVariant = InkSecondaryLight,
    surfaceContainerHigh = CoolContainerHigh,
    outline = CoolOutline,
    outlineVariant = CoolSurfaceVariant,
    error = StatusOfflineLight,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val telemetryColors = if (darkTheme) {
        TelemetryColors(
            channel1 = Channel1CyanDark,
            channel2 = Channel2AmberDark,
            online = StatusOnlineDark,
            warning = StatusWarnDark,
            offline = StatusOfflineDark,
            gridLine = SlateOutline
        )
    } else {
        TelemetryColors(
            channel1 = Channel1CyanLight,
            channel2 = Channel2AmberLight,
            online = StatusOnlineLight,
            warning = StatusWarnLight,
            offline = StatusOfflineLight,
            gridLine = CoolOutline
        )
    }

    CompositionLocalProvider(LocalTelemetryColors provides telemetryColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
