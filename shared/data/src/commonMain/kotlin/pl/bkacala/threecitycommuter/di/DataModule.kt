package pl.bkacala.threecitycommuter.di

import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.model.rail.RailStationCatalog
import pl.bkacala.threecitycommuter.repository.rail.DatabaseRailStationCatalog
import pl.bkacala.threecitycommuter.repository.rail.RailStationsSeedSeeder
import pl.bkacala.threecitycommuter.repository.routes.RealRoutesRepository
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.stops.RealTransitStopsRepository
import pl.bkacala.threecitycommuter.repository.stops.TransitStopsRepository
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.repository.update.RealLastUpdateRepository
import pl.bkacala.threecitycommuter.repository.vehicles.RealVehiclesRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase

expect val platformDataModule: Module

val dataModule = module {
    single<Settings> { Settings() }
    single<LastUpdateRepository> { RealLastUpdateRepository(get()) }
    single<TransitStopsRepository> { RealTransitStopsRepository(get<TransitDataSource>(), get(), get()) }
    single<VehiclesRepository> { RealVehiclesRepository(get(), get<TransitDataSource>(), get()) }
    single<RoutesRepository> { RealRoutesRepository(get()) }
    single { RailStationsSeedSeeder(get(), get()) }
    single<RailStationCatalog> { DatabaseRailStationCatalog(get()) }
    single { GetDeparturesUseCase(get(), get()) }
}
