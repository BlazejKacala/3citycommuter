package pl.bkacala.threecitycommuter.model.schedule

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

/** Provider-qualified identity; sourceId is a route ID or a configured rail carrier. */
data class TimetableLine(
    val id: String,
    val provider: TransitProvider,
    val sourceId: String,
    val number: String,
    val name: String,
    val stopKeys: List<TransitStopKey> = emptyList(),
)

data class TimetableStopTime(
    val stopKey: TransitStopKey,
    val sequence: Int,
    val arrivalTime: Instant? = null,
    val departureTime: Instant? = null,
    val boardingAllowed: Boolean = true,
)

/** One departure instance, not just a route pattern or a repeating trip identifier. */
data class TimetableJourney(
    val id: String,
    val lineId: String,
    val routeId: Int,
    val tripId: Int,
    val directionId: String,
    val headsign: String,
    val stops: List<TimetableStopTime>,
    val isMainVariant: Boolean = false,
)

enum class TimetableAvailability { AVAILABLE, UNSUPPORTED_DATE }

data class LineTimetable(
    val line: TimetableLine,
    val date: LocalDate,
    val journeys: List<TimetableJourney>,
    val availability: TimetableAvailability = TimetableAvailability.AVAILABLE,
    val message: String? = null,
)
