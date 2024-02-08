package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import pl.bkacala.threecitycommuter.model.BusStopsNetworkData
import pl.bkacala.threecitycommuter.model.DepartureList
import pl.bkacala.threecitycommuter.model.VehiclesNetworkData

class KtorNetworkClient(
    private val httpClient: HttpClient,
    private val json: Json
) : NetworkClient {

    companion object {
        private const val BASE_URL = "https://ckan.multimediagdansk.pl"
        private const val BASE_URLV2 = "https://ckan2.multimediagdansk.pl"

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

    override suspend fun getDepartures(stopId: Int): DepartureList {
        val departures = withContext(Dispatchers.IO) {
            httpClient.get("$BASE_URLV2/departures?stopId=$stopId").body<DepartureList>()
        }
        return departures
    }

    override suspend fun getVehicles(): VehiclesNetworkData {
        val vehicles = withContext(Dispatchers.IO) {
            httpClient.get("https://files.cloudgdansk.pl/d/otwarte-dane/ztm/baza-pojazdow.json?v=2")
                .body<VehiclesNetworkData>()
        }
        return vehicles
    }

}