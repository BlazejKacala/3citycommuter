package pl.bkacala.threecitycommuter.di

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.database.CommuterDatabase
import pl.bkacala.threecitycommuter.database.getDatabaseBuilder

val databaseModule: Module = module {
    single<CommuterDatabase> {
        getDatabaseBuilder()
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<CommuterDatabase>().transitStopsDao }
    single { get<CommuterDatabase>().vehiclesDao }
    single { get<CommuterDatabase>().railStationsDao }
}
