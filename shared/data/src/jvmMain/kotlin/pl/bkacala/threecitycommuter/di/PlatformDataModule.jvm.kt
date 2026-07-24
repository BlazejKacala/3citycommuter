package pl.bkacala.threecitycommuter.di

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.repository.location.DesktopLocationRepository
import pl.bkacala.threecitycommuter.repository.location.DesktopPermissionChecker
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker

actual val platformDataModule: Module = module {
    single<LocationRepository> { DesktopLocationRepository() }
    single<PermissionChecker> { DesktopPermissionChecker() }
}
