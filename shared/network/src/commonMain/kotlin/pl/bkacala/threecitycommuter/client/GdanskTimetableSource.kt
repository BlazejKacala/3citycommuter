package pl.bkacala.threecitycommuter.client

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimeResponse
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableAvailability
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.schedule.TimetableStopTime
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

/** Reads one explicit Gdansk feed date at a time and the official dated stop-time endpoint. */
internal class GdanskTimetableSource(
    private val httpClient: io.ktor.client.HttpClient,
    private val json: Json,
    private val gdanskApiClient: GdanskApiClient,
) {
    suspend fun getLines(): List<TimetableLine> {
        val serviceDate = Clock.System.now().toLocalDateTime(WARSAW).date
        val routes = getCatalog<RouteEntry>(ROUTES_URL, serviceDate, "routes")
        return routes.map { route ->
            TimetableLine(
                id = "GDANSK:${route.routeId}",
                provider = TransitProvider.GDANSK,
                sourceId = route.routeId.toString(),
                number = route.routeShortName?.takeIf(String::isNotBlank) ?: route.routeId.toString(),
                name = route.routeLongName?.takeIf(String::isNotBlank)
                    ?: route.routeShortName?.takeIf(String::isNotBlank) ?: "Linia ${route.routeId}",
            )
        }.distinctBy(TimetableLine::id).sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }))
    }

    suspend fun getTimetable(line: TimetableLine, date: LocalDate): LineTimetable {
        if (line.provider != TransitProvider.GDANSK) return unsupported(line, date)
        val routeId = line.sourceId.toIntOrNull() ?: return unsupported(line, date)
        val routes = getCatalog<RouteEntry>(ROUTES_URL, date, "routes")
        val route = routes.firstOrNull { it.routeId == routeId } ?: return unsupported(line, date)
        val spanRoot = json.parseToJsonElement(httpClient.get(STOP_TIMES_SPAN_URL) { expectSuccess = true }.bodyAsText())
        val spans = json.decodeFromJsonElement<StopTimesSpanCatalog>(spanRoot).stopTimesSpans
        val range = spans.firstOrNull { it.agencyId == route.agencyId }
        if (range == null || date < LocalDate.parse(range.startDate) || date > LocalDate.parse(range.endDate)) {
            return unsupported(line, date)
        }

        val trips = getCatalog<TripEntry>(TRIPS_URL, date, "trips")
            .filter { it.routeId == routeId }
            .associateBy(TripEntry::tripId)
        val response = gdanskApiClient.getRouteStopTimes(date.toString(), routeId)
        val rows = response.stopTimes.asSequence()
            .filter { it.routeId == null || it.routeId == routeId }
            .filter { trips[it.tripId]?.type != "NON_PASSENGER" }
            .filter { it.passenger != false && it.nonpassenger != 1 && it.virtual != 1 }
            .mapNotNull { row -> row.toScheduleRow(date) }
            .toList()

        val journeys = rows.groupBy { it.tripId to it.variantId }.flatMap { (patternKey, patternRows) ->
            val bySequence = patternRows.groupBy(ScheduleRow::sequence)
                .mapValues { (_, sequenceRows) -> sequenceRows.sortedBy { it.departure ?: it.arrival } }
            val occurrenceCounts = bySequence.values.map { it.size }.distinct()
            // Missing/uneven sequence occurrences make run-to-run alignment ambiguous.
            if (occurrenceCounts.size != 1) return@flatMap emptyList()
            val runCount = occurrenceCounts.singleOrNull() ?: 0
            val sequenceLists = bySequence.toSortedMap().values
            (0 until runCount).mapNotNull { runIndex ->
                val run = sequenceLists.map { it[runIndex] }
                if (!runTimesAreMonotonic(run)) return@mapNotNull null
                val stopTimes = run.map { row ->
                    TimetableStopTime(
                        stopKey = TransitStopKey(TransitProvider.GDANSK, row.stopId),
                        sequence = row.sequence,
                        arrivalTime = row.arrival,
                        departureTime = row.departure,
                    )
                }
                val metadata = trips[patternKey.first]
                val first = run.first()
                TimetableJourney(
                    id = "gdansk:$routeId:${patternKey.first}:${patternKey.second}:${first.departure ?: first.arrival}:$runIndex",
                    lineId = line.id,
                    routeId = routeId,
                    tripId = patternKey.first,
                    directionId = metadata?.directionId?.let { "direction:$it" }
                        ?: "variant:${patternKey.second}:${stopTimes.joinToString { it.stopKey.sourceStopId.toString() }}",
                    headsign = metadata?.tripHeadsign.orEmpty(),
                    stops = stopTimes,
                    isMainVariant = metadata?.type == "MAIN",
                )
            }
        }.sortedBy { journey -> journey.stops.firstNotNullOfOrNull { it.departureTime ?: it.arrivalTime } }
        return LineTimetable(line, date, journeys)
    }

    private fun runTimesAreMonotonic(run: List<ScheduleRow>): Boolean {
        if (run.any { it.arrival != null && it.departure != null && it.departure < it.arrival }) return false
        val times = run.mapNotNull { it.departure ?: it.arrival }
        return times.zipWithNext().all { (first, second) -> second >= first }
    }

    private fun GdanskRouteStopTimeResponse.toScheduleRow(serviceDate: LocalDate): ScheduleRow? {
        if (passenger == false || nonpassenger == 1 || virtual == 1) return null
        val arrival = arrivalTime?.let { parseServiceTime(it, serviceDate) }
        val departure = departureTime?.let { parseServiceTime(it, serviceDate) }
        if (arrival == null && departure == null) return null
        return ScheduleRow(tripId, variantId ?: 0, stopSequence, stopId, arrival, departure)
    }

    private fun parseServiceTime(raw: String, serviceDate: LocalDate): Instant? = runCatching {
        val value = raw.trim()
        if (value.startsWith("1899-12-30") || value.startsWith("1899-12-31")) {
            val sourceDate = LocalDate.parse(value.substring(0, 10))
            val offsetDays = (sourceDate.toEpochDays() - LocalDate(1899, 12, 30).toEpochDays()).toInt()
            val date = serviceDate.plus(DatePeriod(days = offsetDays))
            val time = LocalTime.parse(value.substringAfter('T').substringBefore('Z').substringBefore('+'))
            return@runCatching LocalDateTime(date, time).toInstant(WARSAW)
        }
        if (value.length >= 19 && value[4] == '-') {
            // Absolute timestamps keep their own date and offset; a local ISO timestamp
            // without an offset is interpreted in the feed's Europe/Warsaw timezone.
            val hasOffset = value.endsWith("Z") || value.substringAfter('T').contains('+') ||
                value.substringAfter('T').drop(1).contains('-')
            if (hasOffset) return@runCatching Instant.parse(value)
            return@runCatching LocalDateTime.parse(value).toInstant(WARSAW)
        }
        LocalDateTime(serviceDate, LocalTime.parse(value)).toInstant(WARSAW)
    }.getOrNull()

    private suspend inline fun <reified T> getCatalog(url: String, date: LocalDate, arrayName: String): List<T> {
        val root = json.parseToJsonElement(httpClient.get(url) { expectSuccess = true }.bodyAsText()) as? JsonObject
            ?: error("Malformed Gdansk $arrayName catalog")
        val day = root[date.toString()] as? JsonObject ?: return emptyList()
        val array = day[arrayName] ?: error("Missing $arrayName for $date in Gdansk catalog")
        return json.decodeFromJsonElement(array)
    }

    private fun unsupported(line: TimetableLine, date: LocalDate) =
        LineTimetable(line, date, emptyList(), TimetableAvailability.UNSUPPORTED_DATE)

    private data class ScheduleRow(
        val tripId: Int,
        val variantId: Int,
        val sequence: Int,
        val stopId: Int,
        val arrival: Instant?,
        val departure: Instant?,
    )

    @Serializable
    private data class RouteEntry(
        val routeId: Int,
        val agencyId: Int,
        val routeShortName: String? = null,
        val routeLongName: String? = null,
    )

    @Serializable
    private data class TripEntry(
        val routeId: Int,
        val tripId: Int,
        val tripHeadsign: String? = null,
        val directionId: Int? = null,
        val type: String? = null,
    )

    @Serializable
    private data class StopTimesSpanEntry(
        val agencyId: Int,
        val startDate: String,
        val endDate: String,
    )

    @Serializable
    private data class StopTimesSpanCatalog(val stopTimesSpans: List<StopTimesSpanEntry>)

    private companion object {
        val WARSAW = TimeZone.of("Europe/Warsaw")
        const val ROUTES_URL = "https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/22313c56-5acf-41c7-a5fd-dc5dc72b3851/download/routes.json"
        const val TRIPS_URL = "https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/b15bb11c-7e06-4685-964e-3db7775f912f/download/trips.json"
        const val STOP_TIMES_SPAN_URL = "https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/9c3d6fed-5394-4ef1-b2c6-c8716999149c/download/stoptimesspan.json"
    }
}
