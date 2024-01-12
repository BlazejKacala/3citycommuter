package pl.bkacala.threecitycommuter.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun NavController.isTopLevelDestinationInHierarchy(destination: TopLvlDestinations) =
    this.currentBackStackEntryAsState().value?.destination?.hierarchy?.any {
        it.route?.contains(destination.name, true) ?: false
    } ?: false