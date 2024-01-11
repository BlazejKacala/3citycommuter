package pl.bkacala.threecitycommuter.ui.nav

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy

fun NavController.isTopLevelDestinationInHierarchy(destination: TopLvlDestinations) =
    this.currentDestination?.hierarchy?.any {
        it.route?.contains(destination.name, true) ?: false
    } ?: false