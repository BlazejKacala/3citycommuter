package pl.bkacala.threecitycommuter.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import pl.bkacala.threecitycommuter.ui.screen.lines.LinesScreen
import pl.bkacala.threecitycommuter.ui.screen.map.MapScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
) {
    NavHost(navController, startDestination = TopLvlDestinations.Map.route) {
        navigation(
            startDestination = Destinations.Maps.route,
            route = TopLvlDestinations.Map.route
        ) {
            composable(Destinations.Maps.route) {
                MapScreen()
            }
        }

        navigation(
            startDestination = Destinations.Lines.route,
            route = TopLvlDestinations.Schedule.route
        ) {
            composable(Destinations.Lines.route) {
                LinesScreen()
            }
        }
    }
}