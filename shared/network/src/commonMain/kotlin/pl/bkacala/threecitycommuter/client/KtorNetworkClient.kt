package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.BusStopsNetworkData
import pl.bkacala.threecitycommuter.model.DepartureList
import pl.bkacala.threecitycommuter.model.RouteNetworkData
import pl.bkacala.threecitycommuter.model.VehiclePositionsNetworkData
import pl.bkacala.threecitycommuter.model.VehiclesNetworkData

internal class KtorNetworkClient(
    private val httpClient: HttpClient,
    @Suppress("unused")
    private val json: Json,
) : NetworkClient {

    companion object {
        private const val BASE_URL = "https://ckan.multimediagdansk.pl"
        private const val BASE_URLV2 = "https://ckan2.multimediagdansk.pl"
        private const val CLOUD_URL = "https://files.cloudgdansk.pl"
    }

    override suspend fun getStops(): BusStopsNetworkData {
        val stops = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get(
                    "$BASE_URL/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/" +
                        "resource/4c4025f0-01bf-41f7-a39f-d156d201b82b/download/stops.json",
                )
                .bodyAsText()
            val payloadByDate = json.decodeFromString<Map<String, BusStopsNetworkData>>(rawBody)

            payloadByDate.values.firstOrNull()
                ?: error("Stops payload is empty")
        }
        return stops
    }

    override suspend fun getDepartures(stopId: Int): DepartureList {
        val departures = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$BASE_URLV2/departures?stopId=$stopId").bodyAsText()
            json.decodeFromString<DepartureList>(rawBody)
        }
        return departures
    }

    override suspend fun getVehicles(): VehiclesNetworkData {
        val vehicles = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$CLOUD_URL/d/otwarte-dane/ztm/baza-pojazdow.json?v=2")
                .bodyAsText()
            json.decodeFromString<VehiclesNetworkData>(rawBody)
        }
        return vehicles
    }

    override suspend fun getVehiclesPositions(): VehiclePositionsNetworkData {
        val vehicles = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$BASE_URLV2/gpsPositions?v=2")
                .bodyAsText()
            json.decodeFromString<VehiclePositionsNetworkData>(rawBody)
        }
        return vehicles
    }

    override suspend fun getRoute(date: String, routeId: Int, tripId: Int): RouteNetworkData {
        val route = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$BASE_URLV2/shapes?date=$date&routeId=$routeId&tripId=$tripId")
                .bodyAsText()
            json.decodeFromString<RouteNetworkData>(rawBody)
        }
        return route
    }
}
