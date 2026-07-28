package pl.bkacala.threecitycommuter.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.client.EmptyGdyniaGtfsSeedSource
import pl.bkacala.threecitycommuter.client.FileBackedGdyniaGtfsSnapshotStorage
import pl.bkacala.threecitycommuter.client.GdyniaGtfsSeedSource
import pl.bkacala.threecitycommuter.client.GdyniaGtfsSnapshotStorage

actual val platformNetworkModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single<GdyniaGtfsSnapshotStorage> { FileBackedGdyniaGtfsSnapshotStorage() }
    single<GdyniaGtfsSeedSource> { EmptyGdyniaGtfsSeedSource() }
}
