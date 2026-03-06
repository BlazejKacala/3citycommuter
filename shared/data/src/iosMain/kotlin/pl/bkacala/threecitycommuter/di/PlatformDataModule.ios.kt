package pl.bkacala.threecitycommuter.di

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.repository.location.IosLocationRepository
import pl.bkacala.threecitycommuter.repository.location.IosPermissionChecker
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker

actual val platformDataModule: Module = module {
    single<LocationRepository> { IosLocationRepository() }
    single<PermissionChecker> { IosPermissionChecker() }
}
