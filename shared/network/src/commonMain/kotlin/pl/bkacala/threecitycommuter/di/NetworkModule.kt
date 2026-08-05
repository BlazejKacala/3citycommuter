package pl.bkacala.threecitycommuter.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.client.CombinedTransitDataSource
import pl.bkacala.threecitycommuter.client.GdanskApiClient
import pl.bkacala.threecitycommuter.client.GdanskTransitDataSource
import pl.bkacala.threecitycommuter.client.GdyniaGtfsPreloader
import pl.bkacala.threecitycommuter.client.GdyniaGtfsStore
import pl.bkacala.threecitycommuter.client.GdyniaTransitDataSource
import pl.bkacala.threecitycommuter.client.KtorGdanskApiClient
import pl.bkacala.threecitycommuter.client.KtorPlkApiClient
import pl.bkacala.threecitycommuter.client.PlkApiClient
import pl.bkacala.threecitycommuter.client.SkmStaticFeed
import pl.bkacala.threecitycommuter.client.SkmTransitDataSource
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.client.ZipEntryReader

expect val platformNetworkModule: Module

val networkModule = module {
    single<Json> {
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
    }

    single<HttpClient> {
        val json: Json = get()
        HttpClient(get()) {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            println("KTOR $message")
                        }
                    }
                level = LogLevel.HEADERS
            }
        }
    }

    single<GdanskApiClient> {
        KtorGdanskApiClient(get(), get())
    }

    single { ZipEntryReader() }
    single { GdyniaGtfsStore(get(), get(), get(), get(), get()) }
    single<GdyniaGtfsPreloader> { get<GdyniaGtfsStore>() }
    single<PlkApiClient> { KtorPlkApiClient(get(), get()) }
    single { SkmStaticFeed(get()) }
    single { GdanskTransitDataSource(get()) }
    single { GdyniaTransitDataSource(get(), get(), get()) }
    single { SkmTransitDataSource(get(), get()) }
    single<TransitDataSource> { CombinedTransitDataSource(get(), get(), get()) }
}
