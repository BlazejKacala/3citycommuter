package pl.bkacala.threecitycommuter.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.client.AssetBackedGdyniaGtfsSeedSource
import pl.bkacala.threecitycommuter.client.FileBackedGdyniaGtfsSnapshotStorage
import pl.bkacala.threecitycommuter.client.GdyniaGtfsSeedSource
import pl.bkacala.threecitycommuter.client.GdyniaGtfsSnapshotStorage

actual val platformNetworkModule: Module = module {
    single<HttpClientEngine> { Android.create() }
    single<GdyniaGtfsSnapshotStorage> { FileBackedGdyniaGtfsSnapshotStorage(androidContext()) }
    single<GdyniaGtfsSeedSource> { AssetBackedGdyniaGtfsSeedSource(androidContext()) }
}
