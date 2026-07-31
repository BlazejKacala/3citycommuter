package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient

internal expect suspend fun downloadUrlToFile(
    httpClient: HttpClient,
    url: String,
    filePath: String,
)
