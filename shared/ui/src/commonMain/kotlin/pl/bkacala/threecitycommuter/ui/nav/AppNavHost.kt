package pl.bkacala.threecitycommuter.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import pl.bkacala.threecitycommuter.ui.screen.lines.LinesScreen
import pl.bkacala.threecitycommuter.ui.screen.map.MapScreen

@Composable
fun AppNavHost(navController: NavHostController, snackbarHostState: SnackbarHostState) {
    NavHost(navController = navController, startDestination = TopLvlDestinations.Map.route) {
        navigation(
            route = TopLvlDestinations.Map.route,
            startDestination = Destinations.Maps.route
        ) {
            composable(Destinations.Maps.route) {
                MapScreen(snackbarHostState)
            }
        }
        navigation(
            route = TopLvlDestinations.Schedule.route,
            startDestination = Destinations.Lines.route
        ) {
            composable(Destinations.Lines.route) {
                LinesScreen()
            }
        }
    }
}
