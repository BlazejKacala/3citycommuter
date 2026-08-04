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
import pl.bkacala.threecitycommuter.model.gdansk.GdanskDepartureResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskDeparturesResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteShapeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimesResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclePositionsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclesResponse
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
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
        val snapshotStorage = InMemoryGdyniaGtfsSnapshotStorage()
        val store = GdyniaGtfsStore(
            httpClient = httpClient,
            json = testJson,
            zipEntryReader = ZipEntryReader(),
            snapshotStorage = snapshotStorage,
            seedSource = InMemoryGdyniaGtfsSeedSource(),
        )

        store.preload()
        val route = store.getRouteForTrip(201)

        assertNotNull(route)
        assertEquals(2, route.shape.size)
        assertEquals(54.0, route.shape[0].latitude)
        assertEquals(18.0, route.shape[0].longitude)
        assertEquals(54.1, route.shape[1].latitude)
        assertEquals(18.1, route.shape[1].longitude)
        assertEquals(listOf(1015), route.stops.map { it.key.sourceStopId })
        assertEquals(gdyniaTripsBody, snapshotStorage.snapshot?.tripsBody)
        assertTrue(snapshotStorage.snapshot?.gtfsZip?.contentEquals(gdyniaGtfsZipBytes) == true)
        assertNotNull(snapshotStorage.snapshot?.downloadedAtEpochMilliseconds)
    }

    @Test
    fun `Gdynia GTFS store falls back to persisted snapshot when network refresh fails`() = runTest {
        val snapshotStorage = InMemoryGdyniaGtfsSnapshotStorage(
            GdyniaGtfsSnapshot(
                tripsBody = gdyniaTripsBody,
                gtfsZip = gdyniaGtfsZipBytes,
                downloadedAtEpochMilliseconds = 1_700_000_000_000,
            ),
        )
        val store = GdyniaGtfsStore(
            httpClient = mockHttpClient(),
            json = testJson,
            zipEntryReader = ZipEntryReader(),
            snapshotStorage = snapshotStorage,
            seedSource = InMemoryGdyniaGtfsSeedSource(
                departureMatchIndex = gdyniaDepartureMatchIndexBody,
                shapeIndex = gdyniaShapeIndexBody,
            ),
        )

        store.preload()
        store.refresh()
        val route = store.getRouteForTrip(201)

        assertNotNull(route)
        assertEquals(2, route.shape.size)
        assertEquals(listOf(1015), route.stops.map { it.key.sourceStopId })
    }

    @Test
    fun `Gdynia GTFS store falls back to bundled indices when persisted snapshot is missing`() = runTest {
        val store = GdyniaGtfsStore(
            httpClient = mockHttpClient(),
            json = testJson,
            zipEntryReader = ZipEntryReader(),
            snapshotStorage = InMemoryGdyniaGtfsSnapshotStorage(),
            seedSource = InMemoryGdyniaGtfsSeedSource(
                departureMatchIndex = gdyniaDepartureMatchIndexBody,
                shapeIndex = gdyniaShapeIndexBody,
            ),
        )

        store.preload()
        val route = store.getRouteForTrip(201)

        assertNotNull(route)
        assertEquals(2, route.shape.size)
        assertEquals(emptyList(), route.stops)
    }

    @Test
    fun `Gdynia GTFS store loads bundled route index without parsing GTFS zip`() = runTest {
        val store = GdyniaGtfsStore(
            httpClient = mockHttpClient(),
            json = testJson,
            zipEntryReader = ZipEntryReader(),
            snapshotStorage = InMemoryGdyniaGtfsSnapshotStorage(),
            seedSource = InMemoryGdyniaGtfsSeedSource(
                departureMatchIndex = gdyniaDepartureMatchIndexBody,
                shapeIndex = gdyniaShapeIndexBody,
            ),
        )

        store.preload()
        val route = store.getRouteForTrip(201)
        val resolvedTripId = store.resolveTripId(
            stopId = 1015,
            departureTime = "10:22",
            headsign = "Kacze Buki",
            fallbackTripId = 999,
        )

        assertNotNull(route)
        assertEquals(2, route.shape.size)
        assertEquals(201, resolvedTripId)
        assertEquals(emptyList(), route.stops)
    }

    @Test
    fun `Gdansk route shape includes ordered passenger stops for selected trip`() = runTest {
        val dataSource = GdanskTransitDataSource(fakeGdanskApiClient())

        val route = dataSource.getRouteShape(TransitProvider.GDANSK, routeId = 6, tripId = 42)

        assertEquals(listOf(8227, 8228), route.stops.map { it.key.sourceStopId })
        assertEquals(listOf(0, 1), route.stops.map { it.sequence })
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
            gtfsStore = GdyniaGtfsStore(
                httpClient,
                testJson,
                ZipEntryReader(),
                InMemoryGdyniaGtfsSnapshotStorage(),
                InMemoryGdyniaGtfsSeedSource(
                    departureMatchIndex = gdyniaDepartureMatchIndexBody,
                    shapeIndex = gdyniaShapeIndexBody,
                ),
            ),
        )

        val departures = dataSource.getDepartures(TransitStopKey(TransitProvider.GDYNIA, 1015))

        assertEquals(1, departures.size)
        assertEquals("181", departures.single().lineNumber)
        assertEquals(10181, departures.single().routeId)
        assertEquals(201, departures.single().tripId)
    }

    @Test
    fun `Gdansk and Gdynia providers expose a consistent domain model for UI fields`() = runTest {
        val gdanskDataSource = GdanskTransitDataSource(fakeGdanskApiClient())
        val gdyniaHttpClient = mockHttpClient(
            "http://api.zdiz.gdynia.pl/pt/stops" to MockResponse.Text(gdyniaStopsBody),
            "http://api.zdiz.gdynia.pl/pt/delays?stopId=1015" to MockResponse.Text(gdyniaDelaysBody),
            "http://api.zdiz.gdynia.pl/pt/routes" to MockResponse.Text(gdyniaRoutesBody),
        )
        val gdyniaDataSource = GdyniaTransitDataSource(
            httpClient = gdyniaHttpClient,
            json = testJson,
            gtfsStore = GdyniaGtfsStore(
                gdyniaHttpClient,
                testJson,
                ZipEntryReader(),
                InMemoryGdyniaGtfsSnapshotStorage(),
                InMemoryGdyniaGtfsSeedSource(
                    departureMatchIndex = gdyniaDepartureMatchIndexBody,
                    shapeIndex = gdyniaShapeIndexBody,
                ),
            ),
        )

        val gdanskStop = gdanskDataSource.getStops().single()
        val gdyniaStop = gdyniaDataSource.getStops().single()
        val gdanskDeparture = gdanskDataSource.getDepartures(gdanskStop.stopKey).single()
        val gdyniaDeparture = gdyniaDataSource.getDepartures(gdyniaStop.stopKey).single()

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

    @Test
    fun `SKM provider exposes stations departures and route shapes without live vehicle tracking`() = runTest {
        val dataSource = SkmTransitDataSource(MockSkmRealtimeDataSource())

        val stops = dataSource.getStops()
        val departures = dataSource.getDepartures(stops.first().stopKey)
        val route = dataSource.getRouteShape(TransitProvider.SKM, departures.first().routeId, departures.first().tripId)

        assertTrue(stops.isNotEmpty())
        assertEquals(TransitProvider.SKM, stops.first().provider)
        assertTrue(departures.isNotEmpty())
        assertEquals("S1", departures.first().lineNumber)
        assertEquals(null, departures.first().vehicleId)
        assertNotNull(route)
        assertTrue(route.shape.isNotEmpty())
        assertEquals(listOf(101, 102, 103, 104, 105), route.stops.map { it.key.sourceStopId })
        assertEquals(false, dataSource.features(TransitProvider.SKM).supportsLiveVehicleTracking)
        assertEquals(false, dataSource.features(TransitProvider.SKM).supportsVehicleMetadata)
        assertEquals(null, dataSource.getVehiclePosition(TransitProvider.SKM, 1))
    }

    private fun fakeGdanskApiClient(): GdanskApiClient =
        object : GdanskApiClient {
            override suspend fun getStops(): GdanskStopsResponse =
                GdanskStopsResponse(
                    lastUpdate = "2026-07-27 10:00:00",
                    stops = listOf(
                        GdanskStopResponse(
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

            override suspend fun getDepartures(stopId: Int): GdanskDeparturesResponse =
                GdanskDeparturesResponse(
                    departures = listOf(
                        GdanskDepartureResponse(
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

            override suspend fun getVehicles(): GdanskVehiclesResponse = GdanskVehiclesResponse(emptyList())

            override suspend fun getVehiclePositions(): GdanskVehiclePositionsResponse =
                GdanskVehiclePositionsResponse(emptyList())

            override suspend fun getRouteShape(
                date: String,
                routeId: Int,
                tripId: Int,
            ): GdanskRouteShapeResponse =
                GdanskRouteShapeResponse(
                    coordinates = listOf(
                        listOf(18.46509, 54.47317),
                        listOf(18.47509, 54.48317),
                    ),
                )

            override suspend fun getRouteStopTimes(
                date: String,
                routeId: Int,
            ): GdanskRouteStopTimesResponse =
                GdanskRouteStopTimesResponse(
                    lastUpdate = "2026-07-27 10:00:00",
                    stopTimes = listOf(
                        GdanskRouteStopTimeResponse(
                            tripId = 42,
                            stopId = 8227,
                            stopSequence = 0,
                            passenger = true,
                        ),
                        GdanskRouteStopTimeResponse(
                            tripId = 42,
                            stopId = 9999,
                            stopSequence = 1,
                            passenger = false,
                        ),
                        GdanskRouteStopTimeResponse(
                            tripId = 42,
                            stopId = 8228,
                            stopSequence = 1,
                            passenger = true,
                        ),
                        GdanskRouteStopTimeResponse(
                            tripId = 99,
                            stopId = 1234,
                            stopSequence = 0,
                            passenger = true,
                        ),
                    ),
                )
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

private class InMemoryGdyniaGtfsSnapshotStorage(
    var snapshot: GdyniaGtfsSnapshot? = null,
) : GdyniaGtfsSnapshotStorage {
    override suspend fun readSnapshot(): GdyniaGtfsSnapshot? = snapshot

    override suspend fun writeSnapshot(snapshot: GdyniaGtfsSnapshot): GdyniaGtfsSnapshot {
        this.snapshot = snapshot
        return snapshot
    }

    override suspend fun writeDownloadedSnapshot(
        tripsBody: String,
        downloadedAtEpochMilliseconds: Long?,
        downloadGtfsZipToPath: suspend (String) -> Unit,
        downloadGtfsZipToBytes: suspend () -> ByteArray,
    ): GdyniaGtfsSnapshot {
        val snapshot = GdyniaGtfsSnapshot(
            tripsBody = tripsBody,
            gtfsZip = downloadGtfsZipToBytes(),
            downloadedAtEpochMilliseconds = downloadedAtEpochMilliseconds,
        )
        this.snapshot = snapshot
        return snapshot
    }
}

private class InMemoryGdyniaGtfsSeedSource(
    private val departureMatchIndex: String? = null,
    private val shapeIndex: String? = null,
) : GdyniaGtfsSeedSource {
    override suspend fun readSeedDepartureMatchIndex(): String? = departureMatchIndex

    override suspend fun readSeedShapeIndex(): String? = shapeIndex
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
          "id": "T999R10181",
          "delayInSeconds": 46,
          "estimatedTime": "10:22",
          "headsign": "Kacze Buki",
          "routeId": 10181,
          "tripId": 999,
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

private val gdyniaDepartureMatchIndexBody = """
    {
      "generatedAtUtc":"2026-07-27T10:00:00Z",
      "sourceGtfs":"test",
      "stopTimeIndex":[
        {
          "stopId": 1015,
          "departures": [
            { "time": "10:22", "tripId": 201, "headsign": "Kacze Buki" }
          ]
        }
      ]
    }
""".trimIndent()

private val gdyniaShapeIndexBody = """
    {
      "generatedAtUtc":"2026-07-27T10:00:00Z",
      "sourceTrips":"test",
      "sourceGtfs":"test",
      "tripShapes":[
        { "tripId": 201, "shapeId": 1 }
      ],
      "shapeRoutes":[
        {
          "shapeId": 1,
          "points": [
            { "latitude": 54.0, "longitude": 18.0 },
            { "latitude": 54.1, "longitude": 18.1 }
          ]
        }
      ]
    }
""".trimIndent()

private val gdyniaGtfsZipBytes = byteArrayOf(
    80, 75, 3, 4, 20, 0, 0, 0, 8, 0, 39, 155.toByte(), 251.toByte(), 92, 31, 66, 58, 142.toByte(),
    52, 0, 0, 0, 80, 0, 0, 0, 10, 0, 0, 0, 115, 104, 97, 112, 101, 115, 46, 116, 120, 116,
    43, 206.toByte(), 72, 44, 72, 141.toByte(), 207.toByte(), 76, 209.toByte(), 41, 6, 51, 10, 74,
    226.toByte(), 115, 18, 75, 144.toByte(), 56, 249.toByte(), 121, 8, 78, 113, 106, 97, 105, 106,
    94, 114, 42, 151.toByte(), 161.toByte(), 142.toByte(), 169.toByte(), 137.toByte(), 158.toByte(),
    129.toByte(), 142.toByte(), 161.toByte(), 5, 136.toByte(), 128.toByte(), 240.toByte(), 12, 65,
    60, 67, 29, 35, 0, 80, 75, 3, 4, 20, 0, 0, 0, 8, 0, 39, 155.toByte(), 251.toByte(), 92,
    224.toByte(), 162.toByte(), 220.toByte(), 218.toByte(), 163.toByte(), 0, 0, 0, 216.toByte(), 0, 0,
    0, 14, 0, 0, 0, 115, 116, 111, 112, 95, 116, 105, 109, 101, 115, 46, 116, 120, 116, 77,
    140.toByte(), 49, 14, 194.toByte(), 48, 12, 69, 119, 78, 193.toByte(), 1, 60, 36, 149.toByte(),
    144.toByte(), 80, 55, 16, 3, 136.toByte(), 129.toByte(), 129.toByte(), 78, 108, 38, 249.toByte(),
    80, 171.toByte(), 37, 9, 78, 10, 130.toByte(), 211.toByte(), 83, 202.toByte(), 130.toByte(),
    254.toByte(), 240.toByte(), 158.toByte(), 45, 251.toByte(), 23, 149.toByte(), 180.toByte(),
    243.toByte(), 196.toByte(), 170.toByte(), 242.toByte(), 224.toByte(), 190.toByte(), 145.toByte(),
    27, 200.toByte(), 35, 177.toByte(), 150.toByte(), 65, 49, 77, 185.toByte(), 196.toByte(),
    239.toByte(), 193.toByte(), 23, 71, 220.toByte(), 7, 4, 247.toByte(), 219.toByte(), 109,
    193.toByte(), 62, 203.toByte(), 53, 80, 18, 215.toByte(), 13, 169.toByte(), 121, 165.toByte(),
    241.toByte(), 83, 99, 58, 92, 46, 147.toByte(), 231.toByte(), 150.toByte(), 19, 54, 146.toByte(),
    75, 163.toByte(), 252.toByte(), 64, 15, 79, 101, 172.toByte(), 75, 81, 66, 25, 205.toByte(),
    117, 40, 167.toByte(), 24, 176.toByte(), 142.toByte(), 234.toByte(), 161.toByte(), 20, 195.toByte(),
    6, 55, 14, 158.toByte(), 158.toByte(), 45, 208.toByte(), 187.toByte(), 150.toByte(), 69, 87,
    206.toByte(), 33, 103, 57, 247.toByte(), 152.toByte(), 85, 198.toByte(), 146.toByte(), 53, 117,
    85, 213.toByte(), 198.toByte(), 252.toByte(), 139.toByte(), 93, 208.toByte(), 146.toByte(),
    246.toByte(), 236.toByte(), 222.toByte(), 152.toByte(), 175.toByte(), 135.toByte(), 78, 200.toByte(),
    140.toByte(), 33, 59, 193.toByte(), 126, 0, 80, 75, 1, 2, 20, 0, 20, 0, 0, 0, 8, 0,
    39, 155.toByte(), 251.toByte(), 92, 31, 66, 58, 142.toByte(), 52, 0, 0, 0, 80, 0, 0, 0,
    10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 115, 104, 97, 112, 101,
    115, 46, 116, 120, 116, 80, 75, 1, 2, 20, 0, 20, 0, 0, 0, 8, 0, 39, 155.toByte(), 251.toByte(),
    92, 224.toByte(), 162.toByte(), 220.toByte(), 218.toByte(), 163.toByte(), 0, 0, 0, 216.toByte(),
    0, 0, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 92, 0, 0, 0, 115, 116,
    111, 112, 95, 116, 105, 109, 101, 115, 46, 116, 120, 116, 80, 75, 5, 6, 0, 0, 0, 0,
    2, 0, 2, 0, 116, 0, 0, 0, 43, 1, 0, 0, 0, 0,
)
