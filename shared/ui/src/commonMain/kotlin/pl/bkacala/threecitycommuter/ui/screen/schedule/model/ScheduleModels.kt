package pl.bkacala.threecitycommuter.ui.screen.schedule.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleProvider(val label: String) { GDANSK("Gdańsk"), GDYNIA("Gdynia"), SKM("SKM") }

enum class SearchCategory(val label: String) { LINES("Linie"), STOPS("Przystanki") }

enum class ScheduleCollection(val title: String) {
    FAVORITES("Ulubione"),
    NEARBY("Przystanki w pobliżu"),
    HISTORY("Ostatnio sprawdzane"),
    OFFLINE("Zapisane rozkłady"),
    ALERTS("Moje alerty"),
}

@Serializable
data class ScheduleStop(val id: String, val name: String, val code: String, val distanceMeters: Int = Int.MAX_VALUE)

@Serializable
data class ScheduleStopTime(
    val stopId: String,
    val sequence: Int,
    val arrival: Instant? = null,
    val departure: Instant? = null,
)

@Serializable
data class ScheduleDirection(val id: String, val headsign: String, val stopIds: List<String>)

@Serializable
data class ScheduleLine(
    val id: String,
    val number: String,
    val name: String,
    val provider: ScheduleProvider,
    /** Union of known real stops across the line's actual journey variants. */
    val stopIds: List<String>,
    val sourceId: String = id,
    val directions: List<ScheduleDirection> = emptyList(),
) {
    fun stops(reversed: Boolean) = direction(reversed)?.stopIds.orEmpty()
    fun direction(reversed: Boolean) = directions.getOrNull(if (reversed && directions.size > 1) 1 else 0)
}

@Serializable
data class ScheduleTrip(
    val lineId: String,
    val date: LocalDate,
    /** Legacy display field; consult [hasKnownBoardingTime] before displaying it. */
    val startMinute: Int,
    val reversed: Boolean,
    val boardingStopId: String,
    val delayMinutes: Int = 0,
    val cancelled: Boolean = false,
    val live: Boolean = false,
    val sourceTripId: String = "",
    val routeDepartureTime: Instant? = null,
    val routeId: Int = 0,
    val tripId: Int = 0,
    val directionId: String = "",
    val headsign: String = "",
    val stopTimes: List<ScheduleStopTime> = emptyList(),
    val hasKnownBoardingTime: Boolean = true,
    val hasPlannedBoardingTime: Boolean = true,
) {
    val id: String get() = sourceTripId.ifEmpty { "$lineId/$date/$startMinute/$directionId/$boardingStopId" }
}

data class ScheduleNotice(val title: String, val description: String, val lineIds: Set<String>)

@Serializable
data class SavedSchedule(
    val lineId: String,
    val date: LocalDate,
    val reversed: Boolean,
    val trips: List<ScheduleTrip>,
    val lineSnapshot: ScheduleLine? = null,
    val stopsSnapshot: List<ScheduleStop> = emptyList(),
) {
    val id: String get() = "$lineId/$date/${trips.firstOrNull()?.directionId.orEmpty()}"
}

@Serializable
data class ScheduleAlert(val trip: ScheduleTrip, val minutesBefore: Int, val notifyDelay: Boolean)

@Serializable
data class SchedulePreferences(
    val favorites: Set<String> = emptySet(),
    val history: List<String> = emptyList(),
    val saved: List<SavedSchedule> = emptyList(),
    val alerts: List<ScheduleAlert> = emptyList(),
)

fun Int.asScheduleTime(): String = "${(this / 60).toString().padStart(2, '0')}:${(this % 60).toString().padStart(2, '0')}"

fun LocalDate.asScheduleDate(): String = "${dayOfMonth.toString().padStart(2, '0')}.${monthNumber.toString().padStart(2, '0')}.$year"
