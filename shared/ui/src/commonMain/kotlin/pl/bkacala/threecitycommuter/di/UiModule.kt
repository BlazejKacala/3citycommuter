package pl.bkacala.threecitycommuter.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.ui.screen.map.MapScreenViewModel
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleScreenViewModel
import pl.bkacala.threecitycommuter.ui.screen.schedule.data.LocalSchedulePreferencesStore
import pl.bkacala.threecitycommuter.ui.screen.schedule.data.SchedulePreferencesStore

val uiModule = module {
    viewModel {
        MapScreenViewModel(
            stopsRepository = get(),
            locationRepository = get(),
            permissionChecker = get(),
            vehiclesRepository = get(),
            getDeparturesUseCase = get(),
            routesRepository = get(),
        )
    }
    single<SchedulePreferencesStore> { LocalSchedulePreferencesStore(get()) }
    viewModel {
        ScheduleScreenViewModel(
            preferencesStore = get(),
            scheduleRepository = get(),
            stopsRepository = get(),
            routesRepository = get(),
            locationRepository = get(),
            permissionChecker = get(),
        )
    }
}
