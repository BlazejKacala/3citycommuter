package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.BusStopsNetworkData

class KtorNetworkClient(
    private val httpClient: HttpClient,
    private val json: Json
) : NetworkClient {

    companion object {
        private const val BASE_URL = "https://ckan.multimediagdansk.pl/"
    }

    override suspend fun getStops(): BusStopsNetworkData {
        val stops = withContext(Dispatchers.IO) {
            val jsonData =
                httpClient.get("$BASE_URL/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/4c4025f0-01bf-41f7-a39f-d156d201b82b/download/stops.json")
                    .body<JsonElement>()
            val dataToDeserialize = jsonData.jsonObject.entries.first().value
            json.decodeFromJsonElement(BusStopsNetworkData.serializer(), dataToDeserialize)
        }
        return stops
    }
}