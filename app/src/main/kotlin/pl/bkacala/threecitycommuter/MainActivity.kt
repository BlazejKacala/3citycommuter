package pl.bkacala.threecitycommuter

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.shreyaspatil.permissionFlow.utils.launch
import dev.shreyaspatil.permissionFlow.utils.registerForPermissionFlowRequestsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.ui.nav.AppNavHost
import pl.bkacala.threecitycommuter.ui.theme.AppTheme

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    throw IllegalStateException("SnackbarHostState is not provided")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestLocationPermission()

        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        keepSplashUntilDataPrepared(splashScreen)

        enableEdgeToEdge()
        setContent {
            AppTheme {
                App()
            }
        }
    }

    private fun keepSplashUntilDataPrepared(splashScreen: SplashScreen) {
        val dataLoaded = mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition {
            !dataLoaded.value
        }
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadBusStopsTypes()
            dataLoaded.value = true
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
            },
            contentWindowInsets = WindowInsets.navigationBars
        )
    }
}
