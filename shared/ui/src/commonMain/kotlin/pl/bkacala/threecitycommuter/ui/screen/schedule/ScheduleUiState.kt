package pl.bkacala.threecitycommuter.ui.screen.schedule

import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleCollection
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleLine
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleNotice
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SchedulePreferences
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleProvider
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleStop
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleTrip
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SearchCategory
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.minuteOfServiceDay
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.toTransitProvider

sealed interface ScheduleLoadState {
    data object Idle : ScheduleLoadState
    data object Loading : ScheduleLoadState
    data object Success : ScheduleLoadState
    data class Error(val message: String) : ScheduleLoadState
    data class Unsupported(val message: String) : ScheduleLoadState
}

data class ScheduleUiState(
    val today: LocalDate,
    val date: LocalDate = today,
    val currentMinute: Int = 0,
    val query: String = "",
    val category: SearchCategory = SearchCategory.LINES,
    val provider: ScheduleProvider? = null,
    val lines: List<ScheduleLine> = emptyList(),
    val stops: List<ScheduleStop> = emptyList(),
    val notices: List<ScheduleNotice> = emptyList(),
    val preferences: SchedulePreferences = SchedulePreferences(),
    val lineId: String? = null,
    val stopId: String? = null,
    val stopSearch: Boolean = false,
    val reversed: Boolean = false,
    val directionId: String? = null,
    val departures: List<ScheduleTrip> = emptyList(),
    val trip: ScheduleTrip? = null,
    val savedScheduleId: String? = null,
    val collection: ScheduleCollection = ScheduleCollection.FAVORITES,
    val message: String? = null,
    val catalogState: ScheduleLoadState = ScheduleLoadState.Loading,
    val stopsState: ScheduleLoadState = ScheduleLoadState.Loading,
    val departuresState: ScheduleLoadState = ScheduleLoadState.Idle,
    val tripState: ScheduleLoadState = ScheduleLoadState.Idle,
    val providerErrors: Map<ScheduleProvider, String> = emptyMap(),
    val noticesMessage: String = "Komunikaty drogowe nie są dostępne w obecnych źródłach danych.",
    val nearbyMessage: String? = null,
    val screenResumed: Boolean = false,
) {
    val selectedLine get() = preferences.saved.firstOrNull { it.id == savedScheduleId }?.lineSnapshot
        ?: lines.firstOrNull { it.id == lineId }
    val selectedStop get() = stops.firstOrNull { it.id == stopId }
        ?: preferences.saved.firstOrNull { it.id == savedScheduleId }?.stopsSnapshot?.firstOrNull { it.id == stopId }
    val availableLines get() = lines.filter { stopId == null || it.stopIds.isEmpty() || stopId in it.stopIds }
    val selectedDirection get() = selectedLine?.directions?.firstOrNull { it.id == directionId }
        ?: selectedLine?.direction(reversed)
    val routeStops get() = selectedDirection?.stopIds.orEmpty().ifEmpty { selectedLine?.stops(reversed).orEmpty() }
        .map(::stop)
    val filteredLines get() = lines.filter {
        (provider == null || it.provider == provider) && "${it.number} ${it.name}".matchesSearch(query)
    }
    val filteredStops get() = stops.filter { stop ->
        "${stop.name} ${stop.code}".matchesSearch(query) &&
            (provider == null || stop.id.substringBefore(':') == provider.toTransitProvider().name)
    }
    val activeNotices get() = notices.filter { lineId == null || lineId in it.lineIds }

    fun line(id: String) = lines.firstOrNull { it.id == id }
        ?: preferences.saved.firstOrNull { it.lineId == id }?.lineSnapshot
        ?: ScheduleLine(id, id.substringAfterLast(':'), "", ScheduleProvider.GDANSK, emptyList())
    fun stop(id: String) = findStop(id) ?: ScheduleStop(id, "Przystanek", "")
    fun destination(line: ScheduleLine): String {
        val direction = line.directions.firstOrNull { it.id == directionId } ?: line.direction(reversed)
        return direction?.headsign?.takeIf(String::isNotBlank)
            ?: direction?.stopIds?.lastOrNull()?.let(::findStop)?.name
            ?: "Kierunek niedostępny"
    }
    fun boardingMinute(trip: ScheduleTrip): Int = trip.stopTimes.firstOrNull { it.stopId == trip.boardingStopId }
        ?.let { it.departure ?: it.arrival }
        ?.minuteOfServiceDay(trip.date)
        ?: trip.startMinute

    private fun findStop(id: String) = stops.firstOrNull { it.id == id }
        ?: preferences.saved.firstOrNull { it.id == savedScheduleId }?.stopsSnapshot?.firstOrNull { it.id == id }
}

private fun String.matchesSearch(query: String): Boolean = normalized().contains(query.trim().normalized())

private fun String.normalized(): String = lowercase().map { character ->
    val index = "ąćęłńóśźż".indexOf(character)
    if (index >= 0) "acelnoszz"[index] else character
}.joinToString("")
