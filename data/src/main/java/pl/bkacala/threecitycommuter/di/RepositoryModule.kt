package pl.bkacala.threecitycommuter.di

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.bkacala.threecitycommuter.repository.stops.RealBusStopsRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
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