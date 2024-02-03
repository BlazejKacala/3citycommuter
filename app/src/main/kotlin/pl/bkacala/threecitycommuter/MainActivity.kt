package pl.bkacala.threecitycommuter

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.shreyaspatil.permissionFlow.utils.launch
import dev.shreyaspatil.permissionFlow.utils.registerForPermissionFlowRequestsResult
import pl.bkacala.threecitycommuter.ui.nav.AppNavHost
import pl.bkacala.threecitycommuter.ui.theme.AppTheme

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    throw IllegalStateException("SnackbarHostState is not provided")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        requestLocationPermission()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                App()
            }
        }
    }
}

private fun ComponentActivity.requestLocationPermission() {
    val permissionLauncher = registerForPermissionFlowRequestsResult()
    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
}

@Composable
fun App() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AppNavHost(navController = navController)

                }
            },
            snackbarHost = {
                SnackbarHost(LocalSnackbarHostState.current)
            }
        )
    }
}
