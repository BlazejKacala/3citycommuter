package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.gdansk.GdanskDeparturesResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteShapeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimesResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclePositionsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclesResponse
import pl.bkacala.threecitycommuter.model.schedule.TimetableAvailability
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GdanskTimetableSourceTest {
    @Test
    fun `passenger variants share provider direction and depot trips are excluded`() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                val body = when {
                    request.url.encodedPath.endsWith("routes.json") -> """{"2026-10-20":{"routes":[{"routeId":6,"agencyId":1}]}}"""
                    request.url.encodedPath.endsWith("stoptimesspan.json") -> """{"stopTimesSpans":[{"agencyId":1,"startDate":"2026-10-20","endDate":"2026-10-20"}]}"""
                    else ->
                        """{"2026-10-20":{"trips":[
                    {"routeId":6,"tripId":44,"directionId":1,"tripHeadsign":"Main","type":"MAIN"},
                    {"routeId":6,"tripId":45,"directionId":1,"tripHeadsign":"School","type":"SIDE"},
                    {"routeId":6,"tripId":46,"directionId":2,"tripHeadsign":"Return","type":"MAIN"},
                    {"routeId":6,"tripId":47,"directionId":1,"tripHeadsign":"Depot","type":"NON_PASSENGER"}]}}"""
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            val api = object : GdanskApiClient by EmptyGdanskApiClient() {
                override suspend fun getRouteStopTimes(date: String, routeId: Int) = GdanskRouteStopTimesResponse(
                    date,
                    (44..47).flatMap { trip ->
                        listOf(
                            row(10, 0, "08:00:00", "08:00:00").copy(tripId = trip, variantId = trip),
                            row(trip, 1, "08:10:00", "08:10:00").copy(tripId = trip, variantId = trip),
                        )
                    },
                )
            }
            val source = GdanskTimetableSource(client, Json { ignoreUnknownKeys = true }, api)
            val result = source.getTimetable(TimetableLine("GDANSK:6", TransitProvider.GDANSK, "6", "6", "6"), LocalDate(2026, 10, 20))
            assertEquals(listOf(44, 45, 46), result.journeys.map { it.tripId })
            assertEquals(listOf("direction:1", "direction:1", "direction:2"), result.journeys.map { it.directionId })
            assertEquals("School", result.journeys[1].headsign)
            assertEquals(listOf(true, false, true), result.journeys.map { it.isMainVariant })
            assertEquals(listOf(10, 45), result.journeys[1].stops.map { it.stopKey.sourceStopId })
        } finally {
            client.close()
        }
    }

    @Test
    fun `dated catalog uses only requested date and groups repeated pattern runs without timezone shifts`() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                val body = when {
                    request.url.encodedPath.endsWith("routes.json") -> {
                        assertTrue(request.url.encodedPath.contains("22313c56-5acf-41c7-a5fd-dc5dc72b3851"))
                        """{"2026-10-20":{"lastUpdate":"2026-10-20","routes":[{"routeId":6,"agencyId":1,"routeShortName":"6","routeLongName":"Tram 6"}]},"2026-10-19":{"lastUpdate":"2026-10-19","routes":[{"routeId":99,"agencyId":9,"routeShortName":"99"}]}}"""
                    }
                    request.url.encodedPath.endsWith("trips.json") ->
                        """{"2026-10-20":{"lastUpdate":"2026-10-20","trips":[{"routeId":6,"tripId":44,"tripHeadsign":"Jelitkowo","directionId":1}]}}"""
                    request.url.encodedPath.endsWith("stopsintrips.json") ->
                        """{"2026-10-20":{"lastUpdate":"2026-10-20","stopsInTrip":[{"routeId":6,"tripId":44,"stopId":10,"stopSequence":0},{"routeId":6,"tripId":44,"stopId":11,"stopSequence":1}]}}"""
                    request.url.encodedPath.endsWith("stoptimesspan.json") ->
                        """{"lastUpdate":"2026-10-20","stopTimesSpans":[{"agencyId":1,"startDate":"2026-10-20","endDate":"2026-10-25"}]}"""
                    else -> error("Unexpected Gdansk catalog request: ${request.url}")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val api = object : GdanskApiClient by EmptyGdanskApiClient() {
            override suspend fun getRouteStopTimes(date: String, routeId: Int): GdanskRouteStopTimesResponse {
                assertEquals("2026-10-20", date)
                assertEquals(6, routeId)
                return GdanskRouteStopTimesResponse(
                    lastUpdate = date,
                    stopTimes = listOf(
                        row(10, 0, "2026-10-20T07:00:00+02:00", "2026-10-20T07:00:00+02:00"),
                        row(10, 0, "2026-10-20T08:00:00+02:00", "2026-10-20T08:00:00+02:00"),
                        row(11, 1, "1899-12-31T00:15:00", "1899-12-31T00:15:00"),
                        row(11, 1, "1899-12-31T01:15:00", "1899-12-31T01:15:00"),
                        row(12, 2, "2026-10-20T07:20:00+02:00", "2026-10-20T07:20:00+02:00", nonpassenger = 1),
                    ),
                )
            }
        }
        val source = GdanskTimetableSource(client, Json { ignoreUnknownKeys = true }, api)
        val line = TimetableLine("GDANSK:6", TransitProvider.GDANSK, "6", "6", "Tram 6")
        val timetable = source.getTimetable(line, LocalDate(2026, 10, 20))
        client.close()

        assertEquals(TimetableAvailability.AVAILABLE, timetable.availability)
        assertEquals(2, timetable.journeys.size)
        assertEquals(listOf("Jelitkowo", "Jelitkowo"), timetable.journeys.map { it.headsign })
        assertEquals("direction:1", timetable.journeys.first().directionId)
        assertEquals(2, timetable.journeys.first().stops.size)
        assertEquals(Instant.parse("2026-10-20T05:00:00Z"), timetable.journeys.first().stops.first().departureTime)
        assertEquals(Instant.parse("2026-10-20T22:15:00Z"), timetable.journeys.first().stops.last().departureTime)
        assertTrue(timetable.journeys.zipWithNext().all { (first, second) -> first.id != second.id })
    }

    @Test
    fun `HTTP errors and malformed catalogs are not empty successes`() = runTest {
        val unavailable = HttpClient(
            MockEngine {
                respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val malformed = HttpClient(
            MockEngine {
                respond("{", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            assertFailsWith<ServerResponseException> {
                GdanskTimetableSource(unavailable, Json.Default, EmptyGdanskApiClient()).getLines()
            }
            assertFailsWith<kotlinx.serialization.SerializationException> {
                GdanskTimetableSource(malformed, Json.Default, EmptyGdanskApiClient()).getLines()
            }
        } finally {
            unavailable.close()
            malformed.close()
        }
    }

    @Test
    fun `missing date does not borrow another days routes`() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                respond("""{"2026-10-20":{"routes":[{"routeId":6,"agencyId":1}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            val source = GdanskTimetableSource(client, Json.Default, EmptyGdanskApiClient())
            val line = TimetableLine("GDANSK:6", TransitProvider.GDANSK, "6", "6", "6")
            val result = source.getTimetable(line, LocalDate(2026, 10, 21))
            assertEquals(TimetableAvailability.UNSUPPORTED_DATE, result.availability)
            assertEquals(1, requests)
            assertTrue(result.journeys.isEmpty())
        } finally {
            client.close()
        }
    }

    private fun row(
        stopId: Int,
        sequence: Int,
        arrival: String,
        departure: String,
        nonpassenger: Int? = null,
    ) = GdanskRouteStopTimeResponse(
        routeId = 6,
        tripId = 44,
        variantId = 3,
        stopId = stopId,
        stopSequence = sequence,
        arrivalTime = arrival,
        departureTime = departure,
        nonpassenger = nonpassenger,
    )
}

private class EmptyGdanskApiClient : GdanskApiClient {
    override suspend fun getStops() = GdanskStopsResponse("", emptyList())
    override suspend fun getDepartures(stopId: Int) = GdanskDeparturesResponse(emptyList())
    override suspend fun getVehicles() = GdanskVehiclesResponse(emptyList())
    override suspend fun getVehiclePositions() = GdanskVehiclePositionsResponse(emptyList())
    override suspend fun getRouteShape(date: String, routeId: Int, tripId: Int) = GdanskRouteShapeResponse(emptyList())
    override suspend fun getRouteStopTimes(date: String, routeId: Int) = GdanskRouteStopTimesResponse("", emptyList())
}
