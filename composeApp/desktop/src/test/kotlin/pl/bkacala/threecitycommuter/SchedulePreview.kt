package pl.bkacala.threecitycommuter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.di.uiModule
import pl.bkacala.threecitycommuter.ui.nav.AppBottomBar
import pl.bkacala.threecitycommuter.ui.nav.TopLvlDestinations
import pl.bkacala.threecitycommuter.ui.nav.scheduleGraph
import pl.bkacala.threecitycommuter.ui.screen.schedule.data.SchedulePreferencesStore
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SchedulePreferences
import pl.bkacala.threecitycommuter.ui.theme.AppTheme

/** Manual visual QA entry point. It shares the production navigation graph, but has no external services. */
fun main() {
    startKoin {
        modules(uiModule, schedulePreviewDataModule, module { single<SchedulePreferencesStore> { PreviewPreferencesStore() } })
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "Podgląd rozkładów", state = rememberWindowState(width = 420.dp, height = 900.dp)) {
            var dark by remember { mutableStateOf(false) }
            val controller = rememberNavController()
            val snackbar = remember { SnackbarHostState() }
            AppTheme(darkTheme = dark) {
                Scaffold(
                    topBar = { TextButton(onClick = { dark = !dark }) { Text(if (dark) "Podgląd: ciemny" else "Podgląd: jasny") } },
                    bottomBar = { AppBottomBar(controller) },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        NavHost(controller, startDestination = TopLvlDestinations.Schedule.route) {
                            scheduleGraph(controller, snackbar)
                            composable(TopLvlDestinations.Map.route) { Text("Podgląd obejmuje zakładkę Rozkład jazdy.") }
                        }
                    }
                }
            }
        }
    }
}

private class PreviewPreferencesStore : SchedulePreferencesStore {
    private var preferences = SchedulePreferences()
    override fun read() = preferences
    override fun write(preferences: SchedulePreferences) {
        this.preferences = preferences
    }
}
