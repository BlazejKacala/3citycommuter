package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.BusStopsNetworkData
import pl.bkacala.threecitycommuter.model.DepartureList
import pl.bkacala.threecitycommuter.model.DepartureNetworkData
import pl.bkacala.threecitycommuter.model.RouteNetworkData
import pl.bkacala.threecitycommuter.model.VehiclePositionsNetworkData
import pl.bkacala.threecitycommuter.model.VehiclesNetworkData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransitDataSourceTest {

    @Test
    fun `Gdynia GTFS store parses ordered route points`() = runTest {
        val httpClient = mockHttpClient(
            "http://api.zdiz.gdynia.pl/pt/trips" to MockResponse.Text(gdyniaTripsBody),
            "http://api.zdiz.gdynia.pl/pt/gtfs.zip" to MockResponse.Bytes(gdyniaGtfsZipBytes),
        )
        val store = GdyniaGtfsStore(httpClient, testJson, ZipEntryReader())

        store.preload()
        val route = store.getRouteForTrip(201)

        assertNotNull(route)
        assertEquals(2, route.shape.size)
        assertEquals(54.0, route.shape[0].latitude)
        assertEquals(18.0, route.shape[0].longitude)
        assertEquals(54.1, route.shape[1].latitude)
        assertEquals(18.1, route.shape[1].longitude)
    }

    @Test
    fun `Gdynia departures use route short name as line number`() = runTest {
        val httpClient = mockHttpClient(
            "http://api.zdiz.gdynia.pl/pt/delays?stopId=1015" to MockResponse.Text(gdyniaDelaysBody),
            "http://api.zdiz.gdynia.pl/pt/routes" to MockResponse.Text(gdyniaRoutesBody),
        )
        val dataSource = GdyniaTransitDataSource(
            httpClient = httpClient,
            json = testJson,
            gtfsStore = GdyniaGtfsStore(httpClient, testJson, ZipEntryReader()),
        )

        val departures = dataSource.getDepartures(1015)

        assertEquals(1, departures.size)
        assertEquals("181", departures.single().lineNumber)
        assertEquals(10181, departures.single().routeId)
    }

    @Test
    fun `Gdansk and Gdynia providers expose a consistent domain model for UI fields`() = runTest {
        val gdanskDataSource = GdanskTransitDataSource(fakeGdanskNetworkClient())
        val gdyniaHttpClient = mockHttpClient(
            "http://api.zdiz.gdynia.pl/pt/stops" to MockResponse.Text(gdyniaStopsBody),
            "http://api.zdiz.gdynia.pl/pt/delays?stopId=1015" to MockResponse.Text(gdyniaDelaysBody),
            "http://api.zdiz.gdynia.pl/pt/routes" to MockResponse.Text(gdyniaRoutesBody),
        )
        val gdyniaDataSource = GdyniaTransitDataSource(
            httpClient = gdyniaHttpClient,
            json = testJson,
            gtfsStore = GdyniaGtfsStore(gdyniaHttpClient, testJson, ZipEntryReader()),
        )

        val gdanskStop = gdanskDataSource.getStops().single()
        val gdyniaStop = gdyniaDataSource.getStops().single()
        val gdanskDeparture = gdanskDataSource.getDepartures(gdanskStop.sourceStopId).single()
        val gdyniaDeparture = gdyniaDataSource.getDepartures(gdyniaStop.sourceStopId).single()

        assertEquals(TransitProvider.GDANSK, gdanskStop.provider)
        assertEquals(TransitProvider.GDYNIA, gdyniaStop.provider)
        assertEquals(8227, gdanskStop.sourceStopId)
        assertEquals(1015, gdyniaStop.sourceStopId)

        assertTrue(gdanskDeparture.lineNumber.isNotBlank())
        assertTrue(gdyniaDeparture.lineNumber.isNotBlank())
        assertNotNull(gdanskDeparture.estimatedTime)
        assertNotNull(gdyniaDeparture.estimatedTime)
        assertNotNull(gdanskDeparture.theoreticalTime)
        assertNotNull(gdyniaDeparture.theoreticalTime)
        assertTrue(gdanskDeparture.routeId > 0)
        assertTrue(gdyniaDeparture.routeId > 0)
        assertTrue(gdanskDeparture.tripId > 0)
        assertTrue(gdyniaDeparture.tripId > 0)
    }

    private fun fakeGdanskNetworkClient(): NetworkClient =
        object : NetworkClient {
            override suspend fun getStops(): BusStopsNetworkData =
                BusStopsNetworkData(
                    lastUpdate = "2026-07-27 10:00:00",
                    stops = listOf(
                        BusStopsNetworkData.BusStopNetworkData(
                            stopId = 8227,
                            stopCode = "04",
                            stopName = "Dabrowa Centrum",
                            stopShortName = "8227",
                            stopDesc = "Gdynia Dabrowa Centrum",
                            subName = "04",
                            date = "2026-07-27",
                            zoneId = 5,
                            zoneName = "Gdynia",
                            virtual = 0,
                            nonpassenger = 0,
                            depot = 0,
                            ticketZoneBorder = 0,
                            onDemand = 0,
                            activationDate = "2026-07-27",
                            stopLat = 54.47317,
                            stopLon = 18.46509,
                            stopUrl = "",
                            locationType = null,
                            parentStation = null,
                            stopTimezone = "Europe/Warsaw",
                            wheelchairBoarding = null,
                        ),
                    ),
                )

            override suspend fun getDepartures(stopId: Int): DepartureList =
                DepartureList(
                    departures = listOf(
                        DepartureNetworkData(
                            id = "1",
                            delayInSeconds = 0,
                            estimatedTime = kotlinx.datetime.Instant.parse("2026-07-27T10:22:00Z"),
                            headsign = "Jelitkowo",
                            routeId = 6,
                            scheduledTripStartTime = kotlinx.datetime.Instant.parse("2026-07-27T10:00:00Z"),
                            tripId = 42,
                            status = "REALTIME",
                            theoreticalTime = kotlinx.datetime.Instant.parse("2026-07-27T10:22:00Z"),
                            timestamp = kotlinx.datetime.Instant.parse("2026-07-27T10:09:33Z"),
                            trip = 3594855,
                            vehicleCode = 9278,
                            vehicleId = 146080,
                            vehicleService = "6",
                        ),
                    ),
                )

            override suspend fun getVehicles(): VehiclesNetworkData = VehiclesNetworkData(emptyList())

            override suspend fun getVehiclesPositions(): VehiclePositionsNetworkData =
                VehiclePositionsNetworkData(emptyList())

            override suspend fun getRoute(date: String, routeId: Int, tripId: Int): RouteNetworkData =
                RouteNetworkData(emptyList())
        }

    private fun mockHttpClient(vararg responses: Pair<String, MockResponse>): HttpClient {
        val mockEngine = MockEngine { request ->
            val response = responses.toMap()[request.url.toString()]
                ?: error("Unexpected request: ${request.url}")
            when (response) {
                is MockResponse.Text -> respond(
                    content = response.value,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                is MockResponse.Bytes -> respond(
                    content = response.value,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
                )
            }
        }

        return HttpClient(engine = mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }
    }
}

private sealed interface MockResponse {
    data class Text(val value: String) : MockResponse
    data class Bytes(val value: ByteArray) : MockResponse
}

private val testJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
}

private val gdyniaTripsBody = """
    [
      { "routeId": 10181, "tripId": 201, "shapeId": 1 }
    ]
""".trimIndent()

private val gdyniaRoutesBody = """
    [
      { "routeId": 10181, "routeShortName": "181" }
    ]
""".trimIndent()

private val gdyniaDelaysBody = """
    {
      "lastUpdate": "2026-07-27 10:09:34",
      "delay": [
        {
          "id": "T201R10181",
          "delayInSeconds": 46,
          "estimatedTime": "10:22",
          "headsign": "Kacze Buki",
          "routeId": 10181,
          "tripId": 201,
          "status": "REALTIME",
          "theoreticalTime": "10:22",
          "timestamp": "10:09:33",
          "trip": 3594855,
          "vehicleCode": 9278,
          "vehicleId": 146080
        }
      ]
    }
""".trimIndent()

private val gdyniaStopsBody = """
    [
      {
        "stopId": 1015,
        "stopCode": "10.15",
        "stopName": "Test Gdynia Stop 01",
        "stopDesc": "",
        "stopLat": "54.5000",
        "stopLon": "18.5000",
        "zoneId": "Gdynia",
        "stopURL": null,
        "locationType": null,
        "parentStation": null,
        "stopTimezone": "Europe/Warsaw",
        "wheelchairBoarding": null,
        "ticketZoneBorder": null
      }
    ]
""".trimIndent()

private val gdyniaGtfsZipBytes = byteArrayOf(
    80, 75, 3, 4, 20, 0, 0, 0, 8, 0, 228.toByte(), 90, 251.toByte(), 92, 101, 60, 250.toByte(),
    143.toByte(), 61, 0, 0, 0, 94, 0, 0, 0, 10, 0, 0, 0, 115, 104, 97, 112, 101, 115, 46, 116,
    120, 116, 43, 206.toByte(), 72, 44, 72, 141.toByte(), 207.toByte(), 76, 209.toByte(), 41, 6,
    51, 10, 74, 226.toByte(), 115, 18, 75, 144.toByte(), 56, 249.toByte(), 121, 8, 78, 113, 106,
    97, 105, 106, 94, 114, 42, 151.toByte(), 161.toByte(), 142.toByte(), 169.toByte(), 137.toByte(),
    158.toByte(), 161.toByte(), 142.toByte(), 161.toByte(), 5, 144.toByte(), 48, 130.toByte(), 240.toByte(),
    12, 64, 60, 32, 193.toByte(), 101, 164.toByte(), 99, 106, 10, 98, 88, 130.toByte(), 8, 0,
    80, 75, 1, 2, 20, 0, 20, 0, 0, 0, 8, 0, 228.toByte(), 90, 251.toByte(), 92, 101, 60,
    250.toByte(), 143.toByte(), 61, 0, 0, 0, 94, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 115, 104, 97, 112, 101, 115, 46, 116, 120, 116, 80, 75, 5, 6, 0,
    0, 0, 0, 1, 0, 1, 0, 56, 0, 0, 0, 101, 0, 0, 0, 0, 0,
)
