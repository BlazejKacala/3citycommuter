package pl.bkacala.threecitycommuter.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLvlDestinations(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Map(
        route = "Map",
        label = "Mapa",
        icon = Icons.Rounded.Home,
    ),
    Schedule(
        route = "Schedule",
        label = "Rozkład jazdy",
        icon = Icons.Rounded.AccountBox,
    ),
}

enum class Destinations(
    val route: String,
) {
    Maps(route = "Maps"),
    Lines(route = "Lines"),
}
