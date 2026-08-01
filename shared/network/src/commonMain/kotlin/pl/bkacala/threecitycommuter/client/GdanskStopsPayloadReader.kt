package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse

internal expect suspend fun readGdanskStopsResponse(
    httpClient: HttpClient,
    json: Json,
    url: String,
): GdanskStopsResponse
