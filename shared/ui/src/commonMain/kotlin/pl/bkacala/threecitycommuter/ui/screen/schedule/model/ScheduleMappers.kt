package pl.bkacala.threecitycommuter.ui.screen.schedule.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

private val warsaw = TimeZone.of("Europe/Warsaw")

fun TransitStopKey.toScheduleId(): String = "${provider.name}:$sourceStopId"

fun TransitStopData.toScheduleStop(distanceMeters: Int = Int.MAX_VALUE) = ScheduleStop(
    id = stopKey.toScheduleId(),
    name = name.ifBlank { "Przystanek $sourceStopId" },
    code = stopCode.orEmpty(),
    distanceMeters = distanceMeters,
)

fun TimetableLine.toScheduleLine(): ScheduleLine = ScheduleLine(
    id = id,
    sourceId = sourceId,
    number = number,
    name = name,
    provider = provider.toScheduleProvider(),
    stopIds = stopKeys.map(TransitStopKey::toScheduleId).distinct(),
)

fun LineTimetable.toScheduleLine(existing: ScheduleLine? = null): ScheduleLine {
    val directions = journeys.groupBy { it.directionId }.map { (directionId, journeysForDirection) ->
        val mainJourneys = journeysForDirection.filter { it.isMainVariant && it.headsign.isNotBlank() }
        val headsign = representativeHeadsign(mainJourneys.ifEmpty { journeysForDirection })
        val journey = journeysForDirection
            .filter { it.headsign == headsign }
            .sortedWith(compareByDescending<TimetableJourney> { it.stops.size }.thenBy { it.id })
            .first()
        val routeStops = journey.orderedStopIds()
        ScheduleDirection(
            id = directionId,
            headsign = headsign,
            stopIds = journeysForDirection
                .sortedBy { it.id }
                .fold(routeStops) { stops, variant -> stops.withVariantStops(variant.orderedStopIds()) },
        )
    }
    val stopIds = journeys.flatMap { journey -> journey.stops.sortedBy { it.sequence }.map { it.stopKey.toScheduleId() } }.distinct()
    return (existing ?: line.toScheduleLine()).copy(
        directions = directions,
        stopIds = stopIds.ifEmpty { existing?.stopIds.orEmpty() },
    )
}

private fun representativeHeadsign(journeys: List<TimetableJourney>): String = journeys
    .filter { it.headsign.isNotBlank() }
    .groupBy { it.headsign }
    .entries
    .sortedWith(
        compareByDescending<Map.Entry<String, List<TimetableJourney>>> { it.value.size }
            .thenByDescending { entry -> entry.value.maxOf { it.stops.size } }
            .thenBy { it.key },
    )
    .firstOrNull()?.key ?: journeys.first().headsign

private fun TimetableJourney.orderedStopIds(): List<String> =
    stops.sortedBy { it.sequence }.map { it.stopKey.toScheduleId() }.distinct()

/** Adds branch stops alongside their nearest shared stop while retaining the representative route order. */
private fun List<String>.withVariantStops(variant: List<String>): List<String> {
    if (variant.isEmpty()) return this
    val representativeStops = toMutableList()
    val representativeSet = representativeStops.toSet()
    val additions = variant.filterNot(representativeSet::contains)
    if (additions.isEmpty()) return this

    var previousRepresentativeStop: String? = null
    var pending = mutableListOf<String>()
    variant.forEach { stopId ->
        if (stopId in representativeSet) {
            if (pending.isNotEmpty()) {
                val insertAt = previousRepresentativeStop?.let { representativeStops.indexOf(it) + 1 }
                    ?: representativeStops.indexOf(stopId)
                representativeStops.addAll(insertAt, pending)
                pending = mutableListOf()
            }
            previousRepresentativeStop = stopId
        } else if (stopId !in representativeStops && stopId !in pending) {
            pending += stopId
        }
    }
    if (pending.isNotEmpty()) {
        val insertAfter = previousRepresentativeStop?.let(representativeStops::indexOf)
            ?: representativeStops.size - 1
        representativeStops.addAll(insertAfter + 1, pending)
    }
    return representativeStops
}

fun LineTimetable.toScheduleTrips(directionId: String?, boardingStopId: String): List<ScheduleTrip> {
    val directionIds = journeys.map { it.directionId }.distinct()
    return journeys
        .filter { directionId == null || it.directionId == directionId }
        .mapNotNull { journey ->
            val boardingStop = journey.stops.firstOrNull { it.stopKey.toScheduleId() == boardingStopId }
            if (boardingStop?.boardingAllowed != true) return@mapNotNull null
            val stopTimes = journey.stops.sortedBy { it.sequence }.map {
                ScheduleStopTime(it.stopKey.toScheduleId(), it.sequence, it.arrivalTime, it.departureTime)
            }
            val boarding = stopTimes.firstOrNull { it.stopId == boardingStopId } ?: return@mapNotNull null
            // An arrival at the end of the route is not a boarding departure.
            if (boarding === stopTimes.lastOrNull()) return@mapNotNull null
            val instant = boarding.departure
            ScheduleTrip(
                lineId = line.id,
                date = date,
                startMinute = instant?.minuteOfServiceDay(date) ?: 0,
                reversed = directionIds.indexOf(journey.directionId) > 0,
                boardingStopId = boardingStopId,
                sourceTripId = journey.id,
                routeDepartureTime = stopTimes.firstOrNull()?.departure,
                routeId = journey.routeId,
                tripId = journey.tripId,
                directionId = journey.directionId,
                headsign = journey.headsign,
                stopTimes = stopTimes,
                hasKnownBoardingTime = instant != null,
                hasPlannedBoardingTime = instant != null,
            )
        }.sortedWith(compareBy<ScheduleTrip> { it.hasKnownBoardingTime.not() }.thenBy { it.startMinute })
}

fun Instant.minuteOfServiceDay(serviceDate: LocalDate): Int {
    val local = toLocalDateTime(warsaw)
    return (local.date.toEpochDays() - serviceDate.toEpochDays()) * 1440 + local.hour * 60 + local.minute
}

fun Instant.isOn(date: LocalDate): Boolean = toLocalDateTime(warsaw).date == date

fun TransitProvider.toScheduleProvider(): ScheduleProvider = when (this) {
    TransitProvider.GDANSK -> ScheduleProvider.GDANSK
    TransitProvider.GDYNIA -> ScheduleProvider.GDYNIA
    TransitProvider.PLK -> ScheduleProvider.SKM
}

fun ScheduleProvider.toTransitProvider(): TransitProvider = when (this) {
    ScheduleProvider.GDANSK -> TransitProvider.GDANSK
    ScheduleProvider.GDYNIA -> TransitProvider.GDYNIA
    ScheduleProvider.SKM -> TransitProvider.PLK
}
