package pl.bkacala.threecitycommuter.di

import org.koin.dsl.module
import pl.bkacala.threecitycommuter.database.CommuterDatabase
import pl.bkacala.threecitycommuter.database.getDatabaseBuilder

val databaseModule = module {
    single<CommuterDatabase> {
        getDatabaseBuilder()
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<CommuterDatabase>().busStopsDao }
    single { get<CommuterDatabase>().vehiclesDao }
    single { get<CommuterDatabase>().busStopTypeDao }
}
