package pl.bkacala.threecitycommuter.di

import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.repository.routes.RealRoutesRepository
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.stops.RealBusStopsRepository
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.repository.update.RealLastUpdateRepository
import pl.bkacala.threecitycommuter.repository.vehicles.RealVehiclesRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase

expect val platformDataModule: Module

val dataModule = module {
    single<Settings> { Settings() }
    single<LastUpdateRepository> { RealLastUpdateRepository(get()) }
    single<BusStopsRepository> { RealBusStopsRepository(get<TransitDataSource>(), get(), get(), get()) }
    single<VehiclesRepository> { RealVehiclesRepository(get(), get<TransitDataSource>(), get()) }
    single<RoutesRepository> { RealRoutesRepository(get()) }
    single { GetDeparturesUseCase(get(), get()) }
}
