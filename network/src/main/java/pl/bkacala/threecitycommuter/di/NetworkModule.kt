package pl.bkacala.threecitycommuter.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.AndroidClientEngine
import io.ktor.client.engine.android.AndroidEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.client.KtorNetworkClient
import pl.bkacala.threecitycommuter.client.NetworkClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkClient(httpClient: HttpClient, json: Json) : NetworkClient {
        return KtorNetworkClient(httpClient, json)
    }

    @Provides
    @Singleton
    fun provideJson() : Json {
        return Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json) : HttpClient {

        val httpClient = HttpClient(
            engine = AndroidClientEngine(config = AndroidEngineConfig())
        ) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        return httpClient
    }

}