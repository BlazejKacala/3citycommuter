package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaDelayResponse
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaRouteNetworkData
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaStopNetworkData
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopId
import pl.bkacala.threecitycommuter.model.transit.supportsLiveVehicleTracking
import pl.bkacala.threecitycommuter.model.transit.supportsRouteShapes
import pl.bkacala.threecitycommuter.model.transit.supportsVehicleMetadata
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import kotlin.time.Duration.Companion.hours

internal class GdyniaTransitDataSource(
    private val httpClient: HttpClient,
    private val json: Json,
    private val gtfsStore: GdyniaGtfsStore,
) : TransitDataSource {
    private val routeNamesMutex = kotlinx.coroutines.sync.Mutex()
    private var routeNamesById: Map<Int, String>? = null

    override fun features(provider: TransitProvider): TransitFeatures =
        TransitFeatures(
            provider = TransitProvider.GDYNIA,
            supportsLiveVehicleTracking = TransitProvider.GDYNIA.supportsLiveVehicleTracking,
            supportsRouteShapes = TransitProvider.GDYNIA.supportsRouteShapes,
            supportsVehicleMetadata = TransitProvider.GDYNIA.supportsVehicleMetadata,
        )

    override suspend fun getStops(): List<BusStopData> {
        val body = httpClient.get(STOPS_URL).body<String>()
        return json.decodeFromString<List<GdyniaStopNetworkData>>(body).map { stop ->
            BusStopData(
                stopId = TransitStopId.toAppId(TransitProvider.GDYNIA, stop.stopId),
                stopCode = stop.stopCode,
                stopName = stop.stopName,
                stopShortName = stop.stopName,
                stopDesc = stop.stopDesc,
                subName = null,
                date = null,
                zoneId = stop.zoneId.toIntOrNull() ?: -1,
                zoneName = stop.zoneId,
                virtual = 0,
                nonpassenger = 0,
                depot = 0,
                ticketZoneBorder = stop.ticketZoneBorder?.toIntOrNull() ?: 0,
                onDemand = false,
                activationDate = null,
                stopLat = stop.stopLat.toDouble(),
                stopLon = stop.stopLon.toDouble(),
                stopUrl = stop.stopUrl,
                locationType = stop.locationType,
                parentStation = stop.parentStation,
                stopTimezone = stop.stopTimezone,
                wheelchairBoarding = stop.wheelchairBoarding,
                isForBuses = true,
                isForTrams = false,
            )
        }
    }

    override suspend fun getDepartures(stopId: Int): List<Departure> {
        val body = httpClient.get("$DELAYS_URL?stopId=$stopId").body<String>()
        val payload = json.decodeFromString<GdyniaDelayResponse>(body)
        val baseDateTime = payload.lastUpdate.toLocalDateTime()
        val routeNames = getRouteNames()
        return payload.delay.map { departure ->
            // Gdynia realtime departures expose a tripId, but it is not reliably the same identifier
            // as the GTFS trip_id used by /pt/trips and shapes.txt. We need the GTFS trip id to find
            // the correct shape, so we resolve it from stop_times using the stop, departure time and
            // headsign, and fall back to the realtime value only when the match stays ambiguous.
            val resolvedTripId = gtfsStore.resolveTripId(
                stopId = stopId,
                departureTime = departure.theoreticalTime ?: departure.estimatedTime,
                headsign = departure.headsign,
                fallbackTripId = departure.tripId,
            )
            Departure(
                id = departure.id,
                delayInSeconds = departure.delayInSeconds,
                estimatedTime = departure.estimatedTime?.toTodayInstant(baseDateTime.date, baseDateTime),
                headsign = departure.headsign,
                lineNumber = routeNames[departure.routeId] ?: departure.routeId.toString(),
                routeId = departure.routeId,
                scheduledTripStartTime = null,
                tripId = resolvedTripId,
                status = departure.status,
                theoreticalTime = departure.theoreticalTime?.toTodayInstant(baseDateTime.date, baseDateTime),
                timestamp = departure.timestamp?.toTodayInstant(baseDateTime.date, baseDateTime),
                trip = departure.trip,
                vehicleCode = departure.vehicleCode,
                vehicleId = departure.vehicleId,
                vehicleService = null,
            )
        }
    }

    override suspend fun getRouteShape(provider: TransitProvider, routeId: Int, tripId: Int): Route? =
        gtfsStore.getRouteForTrip(tripId)

    override suspend fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): VehiclePosition? = null

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> = emptyList()

    private suspend fun getRouteNames(): Map<Int, String> =
        routeNamesMutex.withLock {
            routeNamesById?.let { return it }

            val body = httpClient.get(ROUTES_URL).body<String>()
            val routeNames = json.decodeFromString<List<GdyniaRouteNetworkData>>(body)
                .associate { route ->
                    route.routeId to (route.routeShortName ?: route.routeId.toString())
                }
            routeNamesById = routeNames
            routeNames
        }

    private fun String.toLocalDateTime(): LocalDateTime {
        val (datePart, timePart) = split(" ")
        val date = LocalDate.parse(datePart)
        val time = LocalTime.parse(timePart)
        return LocalDateTime(date, time)
    }

    private fun String.toTodayInstant(date: LocalDate, reference: LocalDateTime): Instant {
        val time = LocalTime.parse(this)
        var candidate = time.atDate(date).toInstant(TimeZone.currentSystemDefault())
        if (candidate < reference.toInstant(TimeZone.currentSystemDefault()).minus(6.hours)) {
            candidate = time.atDate(date.plus(DatePeriod(days = 1))).toInstant(TimeZone.currentSystemDefault())
        }
        return candidate
    }
}

private const val STOPS_URL = "http://api.zdiz.gdynia.pl/pt/stops"
private const val DELAYS_URL = "http://api.zdiz.gdynia.pl/pt/delays"
private const val ROUTES_URL = "http://api.zdiz.gdynia.pl/pt/routes"
