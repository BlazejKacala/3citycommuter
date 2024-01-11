package pl.bkacala.threecitycommuter.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector
import pl.bkacala.threecitycommuter.R

enum class TopLvlDestinations(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector
) {
    Map(
        route = "Map",
        label = R.string.nav_toplvl_map,
        icon = Icons.Rounded.Home
    ),
    Schedule(
        route = "Schedule",
        label = R.string.nav_toplvl_schedule,
        icon = Icons.Rounded.AccountBox
    ),
}

enum class Destinations(
    val route: String
) {
    Maps(route = "Maps"),
    Lines(route = "Lines")
}