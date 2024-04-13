package pl.bkacala.threecitycommuter.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.RealLocationRepository
import pl.bkacala.threecitycommuter.repository.routes.RealRoutesRepository
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.stops.RealBusStopsRepository
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.repository.update.RealLastUpdateRepository
import pl.bkacala.threecitycommuter.repository.vehicles.RealVehiclesRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    internal abstract fun bindBusStopsRepository(
        realRepositoryImpl: RealBusStopsRepository
    ): BusStopsRepository

    @Binds
    @Singleton
    internal abstract fun bindLastUpdateRepository(
        realRepositoryImpl: RealLastUpdateRepository
    ): LastUpdateRepository

    @Binds
    @Singleton
    internal abstract fun bindLocationRepository(
        realRepositoryImpl: RealLocationRepository
    ): LocationRepository

    @Binds
    @Singleton
    internal abstract fun bindVehiclesRepository(
        realRepositoryImpl: RealVehiclesRepository
    ): VehiclesRepository

    @Binds
    @Singleton
    internal abstract fun bindRoutesRepository(
        realRoutesImpl: RealRoutesRepository
    ): RoutesRepository


}