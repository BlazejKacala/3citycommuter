package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.gdansk.GdanskDeparturesResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteShapeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclePositionsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclesResponse

internal class KtorGdanskApiClient(
    private val httpClient: HttpClient,
    @Suppress("unused")
    private val json: Json,
) : GdanskApiClient {

    companion object {
        private const val BASE_URL = "https://ckan.multimediagdansk.pl"
        private const val BASE_URLV2 = "https://ckan2.multimediagdansk.pl"
        private const val CLOUD_URL = "https://files.cloudgdansk.pl"
    }

    override suspend fun getStops(): GdanskStopsResponse {
        val stops = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get(
                "$BASE_URL/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/" +
                    "resource/4c4025f0-01bf-41f7-a39f-d156d201b82b/download/stops.json",
            ).bodyAsText()
            val payloadByDate = json.decodeFromString<Map<String, GdanskStopsResponse>>(rawBody)

            payloadByDate.values.firstOrNull()
                ?: error("Stops payload is empty")
        }
        return stops
    }

    override suspend fun getDepartures(stopId: Int): GdanskDeparturesResponse {
        val departures = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$BASE_URLV2/departures?stopId=$stopId").bodyAsText()
            json.decodeFromString<GdanskDeparturesResponse>(rawBody)
        }
        return departures
    }

    override suspend fun getVehicles(): GdanskVehiclesResponse {
        val vehicles = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$CLOUD_URL/d/otwarte-dane/ztm/baza-pojazdow.json?v=2")
                .bodyAsText()
            json.decodeFromString<GdanskVehiclesResponse>(rawBody)
        }
        return vehicles
    }

    override suspend fun getVehiclePositions(): GdanskVehiclePositionsResponse {
        val vehicles = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$BASE_URLV2/gpsPositions?v=2")
                .bodyAsText()
            json.decodeFromString<GdanskVehiclePositionsResponse>(rawBody)
        }
        return vehicles
    }

    override suspend fun getRouteShape(
        date: String,
        routeId: Int,
        tripId: Int,
    ): GdanskRouteShapeResponse {
        val route = withContext(Dispatchers.IO) {
            val rawBody = httpClient.get("$BASE_URLV2/shapes?date=$date&routeId=$routeId&tripId=$tripId")
                .bodyAsText()
            json.decodeFromString<GdanskRouteShapeResponse>(rawBody)
        }
        return route
    }
}
