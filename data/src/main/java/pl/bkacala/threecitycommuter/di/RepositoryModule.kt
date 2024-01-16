package pl.bkacala.threecitycommuter.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.bkacala.threecitycommuter.repository.stops.RealStopsRepository
import pl.bkacala.threecitycommuter.repository.stops.StopsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStopsRepository(
        realRepositoryImpl: RealStopsRepository
    ): StopsRepository
}