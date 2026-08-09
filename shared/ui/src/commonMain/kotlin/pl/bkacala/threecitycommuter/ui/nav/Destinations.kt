package pl.bkacala.threecitycommuter.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLvlDestinations(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Map(
        route = "Map",
        label = "Mapa",
        icon = Icons.Rounded.Map,
    ),
    Schedule(
        route = "Schedule",
        label = "Rozkład jazdy",
        icon = Icons.Rounded.Schedule,
    ),
}

enum class Destinations(
    val route: String,
) {
    Maps(route = "Maps"),
    ScheduleContent(route = "ScheduleContent"),
}
