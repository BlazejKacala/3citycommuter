package pl.bkacala.threecitycommuter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import pl.bkacala.threecitycommuter.di.dataModule
import pl.bkacala.threecitycommuter.di.databaseModule
import pl.bkacala.threecitycommuter.di.networkModule
import pl.bkacala.threecitycommuter.di.platformDataModule
import pl.bkacala.threecitycommuter.di.platformNetworkModule
import pl.bkacala.threecitycommuter.di.uiModule
import pl.bkacala.threecitycommuter.repository.rail.RailStationsSeedSeeder
import pl.bkacala.threecitycommuter.ui.App

fun main() {
    startKoin {
        modules(
            databaseModule,
            networkModule,
            platformNetworkModule,
            dataModule,
            platformDataModule,
            uiModule,
        )
    }

    runBlocking {
        GlobalContext.get().get<RailStationsSeedSeeder>().seedIfEmpty()
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Przystanek Tr\u00F3jmiasto",
        ) {
            App()
        }
    }
}
