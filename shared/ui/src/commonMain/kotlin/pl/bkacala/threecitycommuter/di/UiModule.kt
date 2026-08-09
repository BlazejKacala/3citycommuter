package pl.bkacala.threecitycommuter.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.ui.screen.map.MapScreenViewModel
import pl.bkacala.threecitycommuter.ui.screen.schedule.ScheduleScreenViewModel

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
    viewModel { ScheduleScreenViewModel() }
}
