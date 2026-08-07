package pl.bkacala.threecitycommuter

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import pl.bkacala.threecitycommuter.client.GdyniaGtfsPreloader
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.repository.rail.RailStationsSeedSeeder
import pl.bkacala.threecitycommuter.ui.App

class MainActivity : ComponentActivity() {

    private val gdyniaGtfsPreloader: GdyniaGtfsPreloader by inject()
    private val railStationsSeedSeeder: RailStationsSeedSeeder by inject()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val dataLoaded = mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !dataLoaded.value }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                railStationsSeedSeeder.seedIfEmpty()
            } catch (throwable: Throwable) {
                logError(LOG_TAG, "Failed to load startup data", throwable)
            }
            dataLoaded.value = true
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                gdyniaGtfsPreloader.preload()
                gdyniaGtfsPreloader.refresh()
            } catch (throwable: Throwable) {
                logError(LOG_TAG, "Failed to warm up Gdynia GTFS cache", throwable)
            }
        }

        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

private const val LOG_TAG = "MainActivity"
