package pl.bkacala.threecitycommuter.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.client.KtorNetworkClient
import pl.bkacala.threecitycommuter.client.NetworkClient

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
                level = LogLevel.HEADERS
            }
        }
    }

    single<NetworkClient> {
        KtorNetworkClient(get(), get())
    }
}
