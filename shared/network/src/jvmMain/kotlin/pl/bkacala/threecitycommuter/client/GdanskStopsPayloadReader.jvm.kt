package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse
import java.net.URL

internal actual suspend fun readGdanskStopsResponse(
    httpClient: HttpClient,
    json: Json,
    url: String,
): GdanskStopsResponse {
    URL(url).openStream().buffered().use { input ->
        val payloadByDate = json.decodeFromStream<Map<String, GdanskStopsResponse>>(input)
        return payloadByDate.values.firstOrNull()
            ?: error("Stops payload is empty")
    }
}
