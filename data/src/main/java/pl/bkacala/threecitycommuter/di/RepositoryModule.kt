package pl.bkacala.threecitycommuter.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.stops.RealBusStopsRepository
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.repository.update.RealLastUpdateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBusStopsRepository(
        realRepositoryImpl: RealBusStopsRepository
    ): BusStopsRepository

    @Binds
    @Singleton
    abstract fun bindLastUpdateRepository(
        realRepositoryImpl: RealLastUpdateRepository
    ): LastUpdateRepository


}