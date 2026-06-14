package pl.bkacala.threecitycommuter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import pl.bkacala.threecitycommuter.ui.nav.AppNavHost
import pl.bkacala.threecitycommuter.ui.theme.AppTheme

@Composable
fun App() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    AppTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    AppNavHost(navController = navController, snackbarHostState = snackbarHostState)
                }
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState)
            },
        )
    }
}
