package pl.bkacala.threecitycommuter.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import org.koin.compose.viewmodel.koinViewModel
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleAction
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleCollectionScreen
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleDestination
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleNavigation
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleNoticesScreen
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleScreen
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleScreenViewModel
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleTimetableScreen
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleTripScreen
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleUiState
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleTheme

fun NavGraphBuilder.scheduleGraph(navController: NavHostController, snackbarHostState: SnackbarHostState) {
    navigation(route = TopLvlDestinations.Schedule.route, startDestination = Destinations.ScheduleContent.route) {
        composable(Destinations.ScheduleContent.route) { entry ->
            ScheduleRoute(entry, navController, snackbarHostState) { state, onAction, navigation -> ScheduleScreen(state, onAction, navigation) }
        }
        composable(Destinations.ScheduleTimetable.route) { entry ->
            ScheduleRoute(entry, navController, snackbarHostState) { state, onAction, navigation -> ScheduleTimetableScreen(state, onAction, navigation) }
        }
        composable(Destinations.ScheduleTrip.route) { entry ->
            ScheduleRoute(entry, navController, snackbarHostState) { state, onAction, navigation -> ScheduleTripScreen(state, onAction, navigation.back) }
        }
        composable(Destinations.ScheduleCollection.route) { entry ->
            ScheduleRoute(entry, navController, snackbarHostState) { state, onAction, navigation -> ScheduleCollectionScreen(state, onAction, navigation) }
        }
        composable(Destinations.ScheduleNotices.route) { entry ->
            ScheduleRoute(entry, navController, snackbarHostState) { state, onAction, navigation -> ScheduleNoticesScreen(state, onAction, navigation.back) }
        }
    }
}

@Composable
private fun ScheduleRoute(
    entry: NavBackStackEntry,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    content: @Composable (ScheduleUiState, (ScheduleAction) -> Unit, ScheduleNavigation) -> Unit,
) {
    val owner = remember(entry) { navController.getBackStackEntry(TopLvlDestinations.Schedule.route) }
    val viewModel = koinViewModel<ScheduleScreenViewModel>(viewModelStoreOwner = owner)
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    DisposableEffect(entry, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAction(
                    ScheduleAction.ScreenResumed(
                        when (entry.destination.route) {
                            Destinations.ScheduleTimetable.route -> ScheduleDestination.TIMETABLE
                            Destinations.ScheduleTrip.route -> ScheduleDestination.TRIP
                            Destinations.ScheduleCollection.route -> ScheduleDestination.COLLECTION
                            Destinations.ScheduleNotices.route -> ScheduleDestination.NOTICES
                            else -> ScheduleDestination.HOME
                        },
                    ),
                )
                Lifecycle.Event.ON_PAUSE -> viewModel.onAction(ScheduleAction.ScreenPaused)
                else -> Unit
            }
        }
        entry.lifecycle.addObserver(observer)
        onDispose { entry.lifecycle.removeObserver(observer) }
    }
    val navigation = remember(navController) {
        ScheduleNavigation(
            timetable = { navController.navigate(Destinations.ScheduleTimetable.route) },
            trip = { navController.navigate(Destinations.ScheduleTrip.route) },
            collection = { navController.navigate(Destinations.ScheduleCollection.route) },
            notices = { navController.navigate(Destinations.ScheduleNotices.route) },
            back = { navController.popBackStack() },
        )
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onAction(ScheduleAction.MessageShown)
        }
    }
    ScheduleTheme { content(state, viewModel::onAction, navigation) }
}
