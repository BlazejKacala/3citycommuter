package pl.bkacala.threecitycommuter.di

import android.content.Context
import com.google.android.gms.location.LocationServices
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.repository.location.AndroidLocationRepository
import pl.bkacala.threecitycommuter.repository.location.AndroidPermissionChecker
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker

actual val platformDataModule: Module = module {
    single { LocationServices.getFusedLocationProviderClient(get<Context>()) }
    single<LocationRepository> { AndroidLocationRepository(get()) }
    single<PermissionChecker> { AndroidPermissionChecker(get<Context>()) }
}
