package pl.bkacala.threecitycommuter.ui.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController

@Composable
fun AppNavigationBar(
    navController: NavHostController
) {
    NavigationBar {
        TopLvlDestinations.entries.forEach {
            AppNavigationBarItem(
                topLvlDestination = it,
                navController = navController
            )
        }
    }
}

@Composable
private fun RowScope.AppNavigationBarItem(
    topLvlDestination: TopLvlDestinations,
    navController: NavHostController
) {
    val context = LocalContext.current
    with(topLvlDestination) {
        NavigationBarItem(
            selected = navController.isTopLevelDestinationInHierarchy(this),
            onClick = {
                navController.navigate(route = route)
            },
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = context.getString(label)
                )
            },
            label = { Text(text = context.getString(label)) }
        )
    }
}