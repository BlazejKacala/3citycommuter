package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse

internal actual suspend fun readGdanskStopsResponse(
    httpClient: HttpClient,
    json: Json,
    url: String,
): GdanskStopsResponse {
    val rawBody = httpClient.get(url).bodyAsText()
    val payloadByDate = json.decodeFromString<Map<String, GdanskStopsResponse>>(rawBody)
    return payloadByDate.values.firstOrNull()
        ?: error("Stops payload is empty")
}
