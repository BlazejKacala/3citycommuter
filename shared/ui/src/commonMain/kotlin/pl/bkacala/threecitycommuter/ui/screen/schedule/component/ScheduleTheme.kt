package pl.bkacala.threecitycommuter.ui.screen.schedule.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import pl.bkacala.threecitycommuter.ui.theme.Accent
import pl.bkacala.threecitycommuter.ui.theme.DarkPrimaryColor
import pl.bkacala.threecitycommuter.ui.theme.LightPrimaryColor

/** Derives Material containers from the existing blue palette, including dialogs and selected chips. */
@Composable
fun ScheduleTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val blueContainer = colors.primary.copy(alpha = 0.12f).compositeOver(colors.surface)
    MaterialTheme(
        colorScheme = colors.copy(
            primary = if (dark) LightPrimaryColor else DarkPrimaryColor,
            onPrimary = if (dark) Color(0xFF101D33) else Color.White,
            secondary = if (dark) LightPrimaryColor else DarkPrimaryColor,
            onSecondary = if (dark) Color(0xFF101D33) else Color.White,
            primaryContainer = blueContainer,
            onPrimaryContainer = colors.onSurface,
            secondaryContainer = blueContainer,
            onSecondaryContainer = colors.onSurface,
            tertiaryContainer = Accent.copy(alpha = 0.15f).compositeOver(colors.surface),
            onTertiaryContainer = colors.onSurface,
            surfaceContainer = blueContainer,
            surfaceContainerHigh = blueContainer,
            surfaceContainerHighest = blueContainer,
        ),
        content = content,
    )
}
