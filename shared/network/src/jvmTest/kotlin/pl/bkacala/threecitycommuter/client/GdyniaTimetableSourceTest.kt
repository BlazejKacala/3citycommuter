package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.schedule.TimetableAvailability
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GdyniaTimetableSourceTest {
    @Test
    fun variantsShareGtfsDirectionWithoutChangingTheirStops() = runTest {
        val feed = GdyniaGtfsTimetableParser(ZipEntryReader()).parse(
            snapshot(
                "calendar_dates.txt" to "service_id,date,exception_type\nweekday,20261021,1",
                "trips.txt" to "route_id,service_id,trip_id,trip_headsign,direction_id\n10181,weekday,201,Main,0\n10181,weekday,202,Short,0\n10181,weekday,203,Return,1",
                "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n201,08:00:00,08:00:00,1015,1\n201,08:10:00,08:10:00,1016,2\n202,09:00:00,09:00:00,1015,1\n202,09:05:00,09:05:00,1017,2\n203,10:00:00,10:00:00,1016,1",
            ),
            10181,
            LocalDate(2026, 10, 21),
        )
        assertEquals(listOf("direction:0", "direction:0", "direction:1"), feed.journeys.map { it.directionId })
        assertEquals("Short", feed.journeys[1].headsign)
        assertEquals(listOf(1015, 1017), feed.journeys[1].stops.map { it.stopKey.sourceStopId })
    }

    @Test
    fun largeExceptionCalendarKeepsOnlySelectedDateAndLine() = runTest {
        val calendar = buildString {
            append("service_id,date,exception_type\n")
            repeat(280_000) { append("other$it,20261020,1\n") }
            append("selected,20261021,1\n")
        }
        val archive = snapshot(
            "calendar_dates.txt" to calendar,
            "trips.txt" to "route_id,service_id,trip_id\n10181,selected,201\n10182,selected,202\n10181,other1,203",
            "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n201,08:00:00,08:00:00,1015,1\n202,09:00:00,09:00:00,1015,1\n203,10:00:00,10:00:00,1015,1",
        )
        val file = Files.createTempFile("timetable-calendar", ".zip")
        try {
            Files.write(file, archive.gtfsZip!!)
            val feed = GdyniaGtfsTimetableParser(ZipEntryReader()).parse(
                archive.copy(gtfsZip = null, gtfsZipFilePath = file.toString()),
                10181,
                LocalDate(2026, 10, 21),
            )
            assertTrue(feed.dateSupported)
            assertEquals(listOf(201), feed.journeys.map { it.tripId })
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun mapsActualRoutesAndCalendarAwareTripsIncludingAfterMidnightTimes() = runTest {
        val date = LocalDate(2026, 10, 19) // Monday
        val snapshot = snapshot(
            "trips.txt" to """
                route_id,service_id,trip_id,trip_headsign,direction_id
                10181,weekday,201,Śródmieście,0
                10181,holiday,202,Redłowo,1
                10182,weekday,301,Other line,0
            """.trimIndent(),
            "calendar.txt" to """
                service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
                weekday,1,1,1,1,1,0,0,20261019,20261025
            """.trimIndent(),
            "calendar_dates.txt" to """
                service_id,date,exception_type
                holiday,20261020,1
                weekday,20261021,2
            """.trimIndent(),
            "stop_times.txt" to """
                trip_id,arrival_time,departure_time,stop_id,stop_sequence
                201,23:55:00,23:55:00,1015,1
                201,24:10:00,24:12:00,1016,2
                202,10:00:00,10:00:00,1017,1
            """.trimIndent(),
        )
        val routesClient = HttpClient(
            MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("/pt/routes"))
                respond(
                    content = """[{"routeId":10181,"routeShortName":"181","routeLongName":"Start - End"},{"routeId":10182,"routeShortName":"182","routeLongName":" "}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val storeClient = HttpClient(MockEngine { error("Fresh GTFS snapshot must not download") })
        try {
            val source = GdyniaTimetableSource(
                routesClient,
                Json { ignoreUnknownKeys = true },
                store(storeClient, snapshot),
            )

            val lines = source.getLines()
            assertEquals(listOf("181", "182"), lines.map { it.number })
            assertEquals(listOf("Start - End", "182"), lines.map { it.name })
            assertEquals(TransitProvider.GDYNIA, lines.first().provider)
            assertEquals("10181", lines.first().sourceId)
            assertEquals("GDYNIA:10181", lines.first().id)

            val monday = source.getTimetable(lines.first(), date)
            assertEquals(TimetableAvailability.AVAILABLE, monday.availability)
            assertEquals(listOf(201), monday.journeys.map { it.tripId })
            assertEquals("GDYNIA:10181", monday.journeys.single().lineId)
            assertEquals("direction:0", monday.journeys.single().directionId)
            assertEquals("Śródmieście", monday.journeys.single().headsign)
            assertEquals(2, monday.journeys.single().stops.size)
            assertEquals("2026-10-19T21:55:00Z", monday.journeys.single().stops[0].departureTime.toString())
            assertEquals("2026-10-19T22:12:00Z", monday.journeys.single().stops[1].departureTime.toString())

            val tuesday = source.getTimetable(lines.first(), LocalDate(2026, 10, 20))
            assertEquals(listOf(201, 202), tuesday.journeys.map { it.tripId }.sorted())
            val wednesday = source.getTimetable(lines.first(), LocalDate(2026, 10, 21))
            assertTrue(wednesday.journeys.isEmpty())
            assertEquals(TimetableAvailability.AVAILABLE, wednesday.availability)

            val outside = source.getTimetable(lines.first(), LocalDate(2026, 11, 1))
            assertEquals(TimetableAvailability.UNSUPPORTED_DATE, outside.availability)
        } finally {
            routesClient.close()
            storeClient.close()
        }
    }

    @Test
    fun missingCalendarDoesNotTreatStopTimesAsDailyService() = runTest {
        val feed = GdyniaGtfsTimetableParser(ZipEntryReader()).parse(
            snapshot(
                "trips.txt" to "route_id,service_id,trip_id\n10181,always,201",
                "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n201,08:00:00,08:00:00,1015,1",
            ),
            routeId = 10181,
            date = LocalDate(2026, 10, 19),
        )
        assertFalse(feed.dateSupported)
        assertTrue(feed.journeys.isEmpty())
    }

    @Test
    fun daylightSavingTransitionUsesGtfsNoonOrigin() = runTest {
        val feed = GdyniaGtfsTimetableParser(ZipEntryReader()).parse(
            snapshot(
                "trips.txt" to "route_id,service_id,trip_id,trip_headsign,direction_id\n10181,sunday,201,End,0",
                "calendar_dates.txt" to "service_id,date,exception_type\nsunday,20261025,1",
                "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n201,02:30:00,02:30:00,1015,1",
            ),
            10181,
            LocalDate(2026, 10, 25),
        )
        assertEquals("2026-10-25T01:30:00Z", feed.journeys.single().stops.single().departureTime.toString())
    }

    private fun store(client: HttpClient, snapshot: GdyniaGtfsSnapshot) = GdyniaGtfsStore(
        httpClient = client,
        json = Json { ignoreUnknownKeys = true },
        zipEntryReader = ZipEntryReader(),
        snapshotStorage = MemorySnapshotStorage(snapshot),
        seedSource = InMemoryGdyniaGtfsSeedSource(),
    )

    private fun snapshot(vararg entries: Pair<String, String>) = GdyniaGtfsSnapshot(
        tripsBody = "[]",
        gtfsZip = zip(entries.toMap()),
        downloadedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
    )

    private fun zip(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { stream ->
            entries.forEach { (name, value) ->
                stream.putNextEntry(ZipEntry(name))
                stream.write(value.toByteArray())
                stream.closeEntry()
            }
        }
        return output.toByteArray()
    }
}

private class MemorySnapshotStorage(private var snapshot: GdyniaGtfsSnapshot?) : GdyniaGtfsSnapshotStorage {
    override suspend fun readDownloadedAtEpochMilliseconds(): Long? = snapshot?.downloadedAtEpochMilliseconds

    override suspend fun readSnapshot(): GdyniaGtfsSnapshot? = snapshot

    override suspend fun writeDownloadedSnapshot(
        tripsBody: String,
        downloadedAtEpochMilliseconds: Long?,
        downloadGtfsZipToPath: suspend (String) -> Unit,
        downloadGtfsZipToBytes: suspend () -> ByteArray,
        validate: suspend (GdyniaGtfsSnapshot) -> Unit,
    ): GdyniaGtfsSnapshot = GdyniaGtfsSnapshot(
        tripsBody = tripsBody,
        gtfsZip = downloadGtfsZipToBytes(),
        downloadedAtEpochMilliseconds = downloadedAtEpochMilliseconds,
    ).also { next ->
        validate(next)
        snapshot = next
    }
}
