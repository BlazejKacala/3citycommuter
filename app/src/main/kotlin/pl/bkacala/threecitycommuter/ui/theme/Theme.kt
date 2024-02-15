package pl.bkacala.threecitycommuter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    secondary = DarkPrimaryColor,
    tertiary = Accent,
    surface = BackgroundDark,
    onSurface = PrimaryTextDark,
    onSurfaceVariant = SecondaryTextDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    secondary = DarkPrimaryColor,
    tertiary = Accent,
    surface = Background,
    onSurface = PrimaryTextLight,
    onSurfaceVariant = SecondaryTextLight
)

object Padding {
    val small = 5.dp
    val normal = 10.dp
    val large = 15.dp
    val big = 20.dp
}


@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}