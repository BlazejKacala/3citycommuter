package pl.bkacala.threecitycommuter.ui.theme

import androidx.compose.ui.graphics.Color
import pl.bkacala.threecitycommuter.model.transit.TransitProvider

val DarkPrimaryColor = Color(0xFF1976D2)
val LightPrimaryColor = Color(0xFFBBDEFB)
val PrimaryColor = Color(0xFF2196F3)
val White = Color(0xFFFFFFFF)
val Background = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF212121)

val Accent = Color(0xFFFFC107)

// Map overlays need to remain distinguishable from route and stop colours on every base map.
val MapVehicleColor = Color(0xFFE65100)
val GdanskStopColor = Color(0xFFD62839)
val GdanskSelectedStopColor = Color(0xFFB71C2D)
val GdyniaStopColor = Color(0xFF0077C8)
val GdyniaSelectedStopColor = Color(0xFF005A9C)
val SkmStopColor = Color(0xFF1B5E20)
val SkmSelectedStopColor = Color(0xFF0D3B12)

val PrimaryTextLight = Color(0xFF212121)
val SecondaryTextLight = Color(0x99212121)
val PrimaryTextDark = Color(0xFFCCCCCC)
val SecondaryTextDark = Color(0x99CCCCCC)

val Divider = Color(0xFFBDBDBD)

fun stopMarkerColor(
    provider: TransitProvider?,
    isSelected: Boolean = false,
): Color {
    return when (provider) {
        TransitProvider.GDANSK -> if (isSelected) GdanskSelectedStopColor else GdanskStopColor
        TransitProvider.GDYNIA -> if (isSelected) GdyniaSelectedStopColor else GdyniaStopColor
        TransitProvider.SKM -> if (isSelected) SkmSelectedStopColor else SkmStopColor
        null -> if (isSelected) DarkPrimaryColor else PrimaryColor
    }
}
