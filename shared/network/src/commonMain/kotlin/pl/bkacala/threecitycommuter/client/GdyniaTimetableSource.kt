package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaRouteNetworkData
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableAvailability
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.schedule.TimetableStopTime
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Gdynia's dated timetable adapter. Live departures remain in GdyniaTransitDataSource. */
internal class GdyniaTimetableSource(
    private val httpClient: HttpClient,
    private val json: Json,
    private val gtfsStore: GdyniaGtfsStore,
) {
    suspend fun getLines(): List<TimetableLine> {
        val routes = httpClient.get(ROUTES_URL) { expectSuccess = true }.body<String>()
        return json.decodeFromString<List<GdyniaRouteNetworkData>>(routes)
            .distinctBy { it.routeId }
            .sortedBy { it.routeId }
            .map { route ->
                val number = route.routeShortName?.takeIf(String::isNotBlank) ?: route.routeId.toString()
                TimetableLine(
                    id = lineId(route.routeId),
                    provider = TransitProvider.GDYNIA,
                    sourceId = route.routeId.toString(),
                    number = number,
                    name = route.routeLongName?.takeIf(String::isNotBlank) ?: number,
                )
            }
    }

    suspend fun getTimetable(
        line: TimetableLine,
        date: LocalDate,
    ): LineTimetable = withContext(Dispatchers.IO) {
        val routeId = line.sourceId.toIntOrNull()
            ?: error("Invalid Gdynia route id: ${line.sourceId}")
        require(line.provider == TransitProvider.GDYNIA) { "Expected a Gdynia timetable line" }

        val snapshot = gtfsStore.getTimetableSnapshot()
            ?: error("Gdynia GTFS data unavailable: no local timetable feed")
        val feed = GdyniaGtfsTimetableParser(ZipEntryReader()).parse(snapshot, routeId, date)
        if (!feed.dateSupported) {
            unsupported(line, date, "Feed GTFS nie zawiera kalendarza potwierdzającego tę datę")
        } else {
            LineTimetable(
                line = line,
                date = date,
                journeys = feed.journeys,
                availability = TimetableAvailability.AVAILABLE,
            )
        }
    }

    private fun unsupported(line: TimetableLine, date: LocalDate, message: String) =
        LineTimetable(
            line = line,
            date = date,
            journeys = emptyList(),
            availability = TimetableAvailability.UNSUPPORTED_DATE,
            message = message,
        )

    private fun lineId(routeId: Int) = "${TransitProvider.GDYNIA.name}:$routeId"

    private companion object {
        const val ROUTES_URL = "http://api.zdiz.gdynia.pl/pt/routes"
    }
}

internal data class GdyniaTimetableFeed(
    val dateSupported: Boolean,
    val journeys: List<TimetableJourney>,
)

/** Parses only the published GTFS snapshot; it never starts an independent import. */
internal class GdyniaGtfsTimetableParser(private val zipEntryReader: ZipEntryReader) {
    suspend fun parse(
        snapshot: GdyniaGtfsSnapshot,
        routeId: Int,
        date: LocalDate,
    ): GdyniaTimetableFeed {
        val serviceCalendars = mutableListOf<ServiceCalendar>()
        forEachRow(snapshot, "calendar.txt", required = false) { row ->
            val start = row.required("start_date").gtfsDate()
            val end = row.required("end_date").gtfsDate()
            if (date in start..end) {
                serviceCalendars += ServiceCalendar(
                    serviceId = row.required("service_id"),
                    startDate = start,
                    endDate = end,
                    weekdays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                        .map { row.required(it).gtfsBoolean() },
                )
            }
        }
        // Feeds can contain hundreds of thousands of exceptions. Retain only this service date.
        val serviceExceptions = mutableListOf<ServiceException>()
        forEachRow(snapshot, "calendar_dates.txt", required = false) { row ->
            val exceptionDate = row.required("date").gtfsDate()
            if (exceptionDate == date) {
                serviceExceptions += ServiceException(
                    serviceId = row.required("service_id"),
                    date = exceptionDate,
                    added = when (row.required("exception_type")) {
                        "1" -> true
                        "2" -> false
                        else -> error("Invalid GTFS exception_type")
                    },
                )
            }
        }
        val inCalendarRange = serviceCalendars.any { date >= it.startDate && date <= it.endDate }
        val isExceptionDate = serviceExceptions.any { it.date == date }
        if (!inCalendarRange && !isExceptionDate) {
            return GdyniaTimetableFeed(dateSupported = false, journeys = emptyList())
        }

        val activeServices = serviceCalendars.asSequence()
            .filter { date >= it.startDate && date <= it.endDate }
            .filter { it.weekdays[date.dayOfWeek.ordinal] }
            .map { it.serviceId }
            .toMutableSet()
        serviceExceptions.asSequence()
            .filter { it.date == date }
            .forEach { exception ->
                if (exception.added) activeServices += exception.serviceId else activeServices -= exception.serviceId
            }

        val tripMetadata = mutableMapOf<Int, TripMetadata>()
        forEachRow(snapshot, "trips.txt") { row ->
            val rowRouteId = row.required("route_id").toIntOrNull()
                ?: error("Invalid GTFS route_id")
            if (rowRouteId != routeId) return@forEachRow
            val serviceId = row.required("service_id")
            if (serviceId !in activeServices) return@forEachRow
            val tripId = row.required("trip_id").toIntOrNull()
                ?: error("Invalid GTFS trip_id")
            tripMetadata[tripId] = TripMetadata(
                routeId = rowRouteId,
                tripId = tripId,
                serviceId = serviceId,
                directionId = row["direction_id"]?.takeIf(String::isNotBlank),
                headsign = row["trip_headsign"]?.takeIf(String::isNotBlank).orEmpty(),
            )
        }

        val stopsByTrip = mutableMapOf<Int, MutableList<TimetableStopTime>>()
        forEachRow(snapshot, "stop_times.txt") { row ->
            val tripId = row.required("trip_id").toIntOrNull() ?: error("Invalid GTFS trip_id in stop_times")
            if (tripId !in tripMetadata) return@forEachRow
            val stopId = row.required("stop_id").toIntOrNull() ?: error("Invalid GTFS stop_id")
            val sequence = row.required("stop_sequence").toIntOrNull() ?: error("Invalid GTFS stop_sequence")
            val arrival = row["arrival_time"]?.takeIf(String::isNotBlank)?.toServiceInstant(date)
            val departure = row["departure_time"]?.takeIf(String::isNotBlank)?.toServiceInstant(date)
            stopsByTrip.getOrPut(tripId) { mutableListOf() }.add(
                TimetableStopTime(
                    stopKey = TransitStopKey(TransitProvider.GDYNIA, stopId),
                    sequence = sequence,
                    arrivalTime = arrival,
                    departureTime = departure,
                    boardingAllowed = row["pickup_type"] != "1",
                ),
            )
        }

        val journeys = tripMetadata.values.mapNotNull { trip ->
            val orderedStops = stopsByTrip[trip.tripId].orEmpty().sortedBy { it.sequence }
            if (orderedStops.isEmpty()) return@mapNotNull null
            val direction = trip.directionId?.let { "direction:$it" }
                ?: trip.headsign.takeIf(String::isNotBlank)?.let { "headsign:$it" }
                ?: ""
            TimetableJourney(
                id = "gdynia/${trip.routeId}/${trip.tripId}/$date",
                lineId = "${TransitProvider.GDYNIA.name}:${trip.routeId}",
                routeId = trip.routeId,
                tripId = trip.tripId,
                directionId = trip.directionId?.let { "direction:$it" }
                    ?: "$direction:${trip.headsign}:${orderedStops.joinToString { it.stopKey.sourceStopId.toString() }}",
                headsign = trip.headsign,
                stops = orderedStops,
            )
        }.sortedBy { journey -> journey.stops.first().departureTime ?: journey.stops.first().arrivalTime }

        return GdyniaTimetableFeed(dateSupported = true, journeys = journeys)
    }

    private suspend fun forEachRow(
        snapshot: GdyniaGtfsSnapshot,
        name: String,
        required: Boolean = true,
        onRow: (Map<String, String>) -> Unit,
    ) {
        val context = currentCoroutineContext()
        var headers: List<String>? = null
        var foundHeader = false
        val found = when {
            snapshot.gtfsZipFilePath != null -> zipEntryReader.readEntryLines(snapshot.gtfsZipFilePath, name) { raw ->
                context.ensureActive()
                val parsed = parseLine(raw, headers, onRow)
                if (headers == null && parsed != null) {
                    headers = parsed
                    foundHeader = true
                }
            }
            snapshot.gtfsZip != null -> zipEntryReader.readEntryLines(snapshot.gtfsZip, name) { raw ->
                context.ensureActive()
                val parsed = parseLine(raw, headers, onRow)
                if (headers == null && parsed != null) {
                    headers = parsed
                    foundHeader = true
                }
            }
            else -> false
        }
        if (!found && required) error("Missing $name in Gdynia GTFS archive")
        if (found && !foundHeader) error("Empty $name in Gdynia GTFS archive")
    }

    private fun parseLine(
        raw: String,
        currentHeaders: List<String>?,
        onRow: (Map<String, String>) -> Unit,
    ): List<String>? {
        val line = raw.removePrefix("\uFEFF").trimEnd('\r')
        if (line.isBlank()) return currentHeaders
        val columns = line.parseCsv()
        if (currentHeaders == null) return columns
        onRow(
            currentHeaders.mapIndexedNotNull { index, header ->
                columns.getOrNull(index)?.let { header to it }
            }.toMap(),
        )
        return currentHeaders
    }
}

private data class ServiceCalendar(
    val serviceId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weekdays: List<Boolean>,
)

private data class ServiceException(val serviceId: String, val date: LocalDate, val added: Boolean)

private data class TripMetadata(
    val routeId: Int,
    val tripId: Int,
    val serviceId: String,
    val directionId: String?,
    val headsign: String,
)

private fun Map<String, String>.required(key: String): String =
    this[key]?.takeIf(String::isNotBlank) ?: error("Missing GTFS $key")

private fun String.gtfsBoolean(): Boolean = when (this) {
    "0" -> false
    "1" -> true
    else -> error("Invalid GTFS service weekday flag")
}

private fun String.gtfsDate(): LocalDate =
    LocalDate.parse("${substring(0, 4)}-${substring(4, 6)}-${substring(6, 8)}")

private fun String.toServiceInstant(serviceDate: LocalDate): Instant {
    val pieces = split(':')
    require(pieces.size == 3) { "Invalid GTFS time" }
    val hours = pieces[0].toIntOrNull() ?: error("Invalid GTFS hour")
    val minutes = pieces[1].toIntOrNull() ?: error("Invalid GTFS minute")
    val seconds = pieces[2].substringBefore('.').toIntOrNull() ?: error("Invalid GTFS second")
    require(hours >= 0 && minutes in 0..59 && seconds in 0..59) { "Invalid GTFS time" }
    // GTFS defines times as elapsed seconds from local noon minus twelve hours.
    // Using local midnight would shift departures on daylight-saving transition days.
    val origin = LocalDateTime(serviceDate, LocalTime(12, 0)).toInstant(TimeZone.of("Europe/Warsaw")) - 12.hours
    return origin + (hours.toLong() * 3600 + minutes * 60 + seconds).seconds
}

private fun String.parseCsv(): List<String> {
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == '"' && quoted && getOrNull(index + 1) == '"' -> {
                field.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                fields += field.toString()
                field.clear()
            }
            else -> field.append(char)
        }
        index++
    }
    fields += field.toString()
    return fields
}
