package pl.bkacala.threecitycommuter.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNetworkModule: Module = module {
    single<HttpClientEngine> { Java.create() }
}
