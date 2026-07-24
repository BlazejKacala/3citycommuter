package pl.bkacala.threecitycommuter

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pl.bkacala.threecitycommuter.database.initDatabaseContext
import pl.bkacala.threecitycommuter.di.dataModule
import pl.bkacala.threecitycommuter.di.databaseModule
import pl.bkacala.threecitycommuter.di.networkModule
import pl.bkacala.threecitycommuter.di.platformDataModule
import pl.bkacala.threecitycommuter.di.platformNetworkModule
import pl.bkacala.threecitycommuter.di.uiModule

class CommuterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initDatabaseContext(this)
        startKoin {
            androidContext(this@CommuterApp)
            modules(
                databaseModule,
                networkModule,
                platformNetworkModule,
                dataModule,
                platformDataModule,
                uiModule,
            )
        }
    }
}
