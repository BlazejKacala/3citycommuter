package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableAvailability
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.schedule.ScheduleRepository
import pl.bkacala.threecitycommuter.repository.stops.TransitStopsRepository
import pl.bkacala.threecitycommuter.ui.screen.schedule.data.SchedulePreferencesStore
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SavedSchedule
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleAlert
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleCollection
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleDirection
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleLine
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleProvider
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleStopTime
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleTrip
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.minuteOfServiceDay
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.toScheduleId
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.toScheduleLine
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.toScheduleProvider
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.toScheduleStop
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.toScheduleTrips
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.toTransitProvider
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

class ScheduleScreenViewModel(
    private val preferencesStore: SchedulePreferencesStore,
    private val scheduleRepository: ScheduleRepository,
    private val stopsRepository: TransitStopsRepository,
    private val routesRepository: RoutesRepository,
    private val locationRepository: LocationRepository,
    private val permissionChecker: PermissionChecker,
    private val now: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Warsaw")) },
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        ScheduleUiState(
            today = now().date,
            currentMinute = now().hour * 60 + now().minute,
            preferences = preferencesStore.read(),
        ),
    )
    val uiState = mutableState.asStateFlow()

    private var activeDestination = ScheduleDestination.TIMETABLE
    private var catalogJobs = mutableMapOf<ScheduleProvider, Job>()
    private var stopsJob: Job? = null
    private var timetableJob: Job? = null
    private var pollingJob: Job? = null
    private var liveRequestJob: Job? = null
    private var tripRouteJob: Job? = null
    private var locationJob: Job? = null

    // Retain at most the selected planned schedule for five minutes; live data is never cached here.
    private var loadedTimetable: LineTimetable? = null
    private var loadedStation: TransitStopKey? = null
    private var loadedAt: Instant? = null
    private val sourceLines = mutableMapOf<String, TimetableLine>()
    private val sourceStops = mutableMapOf<String, pl.bkacala.threecitycommuter.model.stops.TransitStopData>()

    init {
        loadCatalogs()
        loadStops()
    }

    fun onAction(action: ScheduleAction) {
        val before = mutableState.value
        val next = when (action) {
            is ScheduleAction.QueryChanged -> before.copy(query = action.value)
            is ScheduleAction.CategoryChanged -> before.copy(category = action.value)
            is ScheduleAction.ProviderChanged -> before.copy(
                provider = action.value,
                lineId = if (before.stopSearch) null else before.lineId,
                directionId = null,
            )
            is ScheduleAction.OpenLine -> openLine(before, action.id)
            is ScheduleAction.OpenStop -> before.copy(
                stopId = action.id, lineId = null, provider = null, stopSearch = true,
                reversed = false, directionId = null, date = before.today, savedScheduleId = null,
                preferences = history(before, "stop:${action.id}"), departures = emptyList(), trip = null,
            )
            is ScheduleAction.LineChanged -> before.copy(lineId = action.id, directionId = null, savedScheduleId = null)
            is ScheduleAction.StopChanged -> before.copy(stopId = action.id, savedScheduleId = null)
            ScheduleAction.ReverseDirection -> cycleDirection(before)
            is ScheduleAction.DirectionChanged -> selectDirection(before, action.id)
            is ScheduleAction.DateChanged -> before.copy(date = action.value, savedScheduleId = null, trip = null)
            is ScheduleAction.OpenTrip -> before.copy(trip = action.trip, tripState = ScheduleLoadState.Loading)
            is ScheduleAction.OpenCollection -> before.copy(collection = action.collection)
            ScheduleAction.MessageShown -> before.copy(message = null)
            is ScheduleAction.ScreenResumed -> before.copy(screenResumed = true)
            ScheduleAction.ScreenPaused -> before.copy(screenResumed = false)
            ScheduleAction.Retry -> before
            else -> updatePreferences(before, action)
        }
        if (next.preferences != before.preferences) {
            try {
                preferencesStore.write(next.preferences)
            } catch (error: Exception) {
                logError(LOG_TAG, "Failed to save timetable preferences", error)
                mutableState.value = before.copy(message = "Nie udało się zapisać ustawień. Spróbuj ponownie.")
                return
            }
        }
        mutableState.value = next

        when (action) {
            is ScheduleAction.ScreenResumed -> {
                activeDestination = action.destination
                updateClock()
                when (activeDestination) {
                    ScheduleDestination.TIMETABLE -> {
                        if (timetableJob?.isActive != true) reloadSelection()
                        startPolling()
                    }
                    ScheduleDestination.TRIP -> {
                        val trip = mutableState.value.trip
                        if (trip != null && tripRouteJob?.isActive != true && next.tripState != ScheduleLoadState.Success) loadTripRoute(trip)
                        startPolling()
                    }
                    ScheduleDestination.COLLECTION -> if (next.collection == ScheduleCollection.NEARBY) loadNearbyStops()
                    else -> Unit
                }
            }
            ScheduleAction.ScreenPaused -> stopPolling()
            ScheduleAction.Retry -> retryCurrent()
            is ScheduleAction.OpenTrip -> loadTripRoute(action.trip)
            is ScheduleAction.OpenLine, is ScheduleAction.OpenStop, is ScheduleAction.LineChanged,
            is ScheduleAction.StopChanged, is ScheduleAction.DirectionChanged, ScheduleAction.ReverseDirection,
            is ScheduleAction.DateChanged, is ScheduleAction.OpenSaved,
            -> reloadSelection()
            is ScheduleAction.OpenCollection -> if (action.collection == ScheduleCollection.NEARBY) loadNearbyStops()
            else -> Unit
        }
    }

    private fun loadCatalogs() {
        ScheduleProvider.entries.forEach { provider ->
            catalogJobs[provider]?.cancel()
            catalogJobs[provider] = viewModelScope.launch {
                try {
                    scheduleRepository.getLines(provider.toTransitProvider()).collect { domainLines ->
                        domainLines.forEach { sourceLines[it.id] = it }
                        val mapped = domainLines.map(TimetableLine::toScheduleLine)
                        mutableState.value = mutableState.value.let { state ->
                            val all = (state.lines.filterNot { it.provider == provider } + mapped).distinctBy { it.id }
                            state.copy(
                                lines = all,
                                providerErrors = state.providerErrors - provider,
                                catalogState = ScheduleLoadState.Success,
                            )
                        }
                    }
                    mutableState.value = mutableState.value.copy(catalogState = ScheduleLoadState.Success)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    logError(LOG_TAG, "Failed to load schedule line catalog for $provider", error)
                    val message = error.message ?: "Nie udało się wczytać katalogu linii."
                    mutableState.value = mutableState.value.let { state ->
                        val errors = state.providerErrors + (provider to message)
                        state.copy(
                            providerErrors = errors,
                            catalogState = if (state.lines.isNotEmpty()) ScheduleLoadState.Success else ScheduleLoadState.Error(message),
                        )
                    }
                }
            }
        }
    }

    private fun loadStops() {
        stopsJob?.cancel()
        stopsJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(stopsState = ScheduleLoadState.Loading)
            try {
                var received = false
                stopsRepository.getTransitStops().collect { data ->
                    received = true
                    sourceStops.clear()
                    data.forEach { sourceStops[it.stopKey.toScheduleId()] = it }
                    val mapped = data.map { it.toScheduleStop() }
                    mutableState.value = mutableState.value.copy(stops = mapped, stopsState = ScheduleLoadState.Success)
                }
                if (!received) mutableState.value = mutableState.value.copy(stopsState = ScheduleLoadState.Success)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                logError(LOG_TAG, "Failed to load schedule stops", error)
                mutableState.value = mutableState.value.copy(stopsState = ScheduleLoadState.Error(error.message ?: "Nie udało się wczytać przystanków."))
            }
        }
    }

    private fun reloadSelection() {
        timetableJob?.cancel()
        liveRequestJob?.cancel()
        val state = mutableState.value
        if (state.savedScheduleId != null) {
            val saved = state.preferences.saved.firstOrNull { it.id == state.savedScheduleId }
            mutableState.value = state.copy(departures = saved?.trips.orEmpty(), departuresState = ScheduleLoadState.Success)
            return
        }
        val line = state.selectedLine
        if (line != null) {
            loadTimetable(line)
        } else if (state.stopSearch && state.stopId != null) {
            if (state.date != state.today) {
                mutableState.value = state.copy(
                    departures = emptyList(),
                    departuresState = ScheduleLoadState.Unsupported("Odjazdy przystanku są dostępne na dziś. Wybierz linię, aby sprawdzić rozkład na inny dzień."),
                )
            } else {
                loadLiveDepartures()
            }
        }
    }

    private fun loadTimetable(line: ScheduleLine) {
        val request = mutableState.value
        val station = if (line.provider == ScheduleProvider.SKM) request.stopId?.let(::findStopKey) else null
        val cached = loadedTimetable?.takeIf {
            it.line.id == line.id && it.date == request.date && loadedStation == station &&
                loadedAt?.let { time -> Clock.System.now() - time < 5.minutes } == true
        }
        if (cached != null) {
            applyTimetable(cached)
            return
        }
        timetableJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(departures = emptyList(), departuresState = ScheduleLoadState.Loading)
            try {
                val sourceLine = sourceLines[line.id] ?: line.asTimetableLine()
                scheduleRepository.getTimetable(sourceLine, request.date, station).collect { timetable ->
                    val current = mutableState.value
                    if (current.lineId != line.id || current.date != request.date || current.stopId != request.stopId) return@collect
                    loadedTimetable = timetable.takeIf { it.journeys.sumOf { trip -> trip.stops.size } <= MAX_RETAINED_STOP_TIMES }
                    loadedStation = station
                    loadedAt = Clock.System.now()
                    applyTimetable(timetable)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                logError(LOG_TAG, "Failed to load timetable for ${line.id} ${request.date}", error)
                if (mutableState.value.lineId == line.id && mutableState.value.date == request.date) {
                    mutableState.value = mutableState.value.copy(departuresState = ScheduleLoadState.Error("Nie udało się wczytać rozkładu. Sprawdź połączenie i spróbuj ponownie."))
                }
            }
        }
    }

    private fun applyTimetable(timetable: LineTimetable) {
        val state = mutableState.value
        if (timetable.availability == TimetableAvailability.UNSUPPORTED_DATE) {
            mutableState.value = state.copy(
                departures = emptyList(),
                departuresState = ScheduleLoadState.Unsupported(timetable.message ?: "Dostawca nie opublikował rozkładu dla tej daty."),
            )
            return
        }
        val line = timetable.toScheduleLine(state.selectedLine)
        val direction = line.directions.firstOrNull { it.id == state.directionId }
            ?: line.directions.firstOrNull { state.stopId in it.stopIds }
            ?: line.directions.firstOrNull()
        val boarding = state.stopId?.takeIf { state.stopSearch || it in direction?.stopIds.orEmpty() }
            ?: direction?.stopIds?.firstOrNull() ?: line.stopIds.firstOrNull()
        val trips = boarding?.let { timetable.toScheduleTrips(direction?.id, it) }.orEmpty()
        mutableState.value = state.copy(
            lines = state.lines.map { if (it.id == line.id) line else it }.let { lines -> if (lines.none { it.id == line.id }) lines + line else lines },
            directionId = direction?.id,
            reversed = line.directions.indexOf(direction) == 1,
            stopId = boarding,
            departures = trips,
            departuresState = ScheduleLoadState.Success,
        )
    }

    private fun loadLiveDepartures() {
        val state = mutableState.value
        val key = state.stopId?.let(::findStopKey) ?: return
        timetableJob?.cancel()
        timetableJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(departures = emptyList(), departuresState = ScheduleLoadState.Loading)
            queryLiveStopOnce(state, key)
        }
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val provider = mutableState.value.stopId?.let(::findStopKey)?.provider
                delay(if (provider == TransitProvider.PLK) RAIL_POLL_INTERVAL else CITY_POLL_INTERVAL)
                updateClock()
                val state = mutableState.value
                val stopKey = state.stopId?.let(::findStopKey)
                if (activeDestination in setOf(ScheduleDestination.TIMETABLE, ScheduleDestination.TRIP) && stopKey != null && state.date == state.today && state.savedScheduleId == null && timetableJob?.isActive != true) {
                    liveRequestJob?.cancel()
                    liveRequestJob = viewModelScope.launch {
                        if (state.selectedLine != null) {
                            queryLineLiveOnce(state, stopKey)
                        } else if (state.stopSearch) {
                            queryLiveStopOnce(state, stopKey)
                        }
                    }
                    liveRequestJob?.join()
                }
            }
        }
    }

    private fun updateClock() {
        val time = now()
        val state = mutableState.value
        val followToday = state.savedScheduleId == null && state.date == state.today
        mutableState.value = state.copy(
            today = time.date,
            date = if (followToday) time.date else state.date,
            currentMinute = time.hour * 60 + time.minute,
        )
        if (followToday && state.today != time.date && activeDestination == ScheduleDestination.TIMETABLE) reloadSelection()
    }

    private suspend fun queryLiveStopOnce(requestState: ScheduleUiState, stopKey: TransitStopKey) {
        try {
            stopsRepository.getDepartures(stopKey).collect { departures ->
                val current = mutableState.value
                if (current.stopId != requestState.stopId || current.date != requestState.date || current.lineId != requestState.lineId || !current.stopSearch) return@collect
                val allTrips = departures.mapNotNull { it.toScheduleTrip(stopKey, current.date, current.lines) }
                    .sortedWith(compareBy<ScheduleTrip> { !it.hasKnownBoardingTime }.thenBy { it.startMinute })
                val selectedLine = current.selectedLine
                val trips = if (selectedLine == null) allTrips else allTrips.filter { it.lineId == selectedLine.id }
                val addedLines = if (selectedLine != null) {
                    emptyList()
                } else {
                    trips.mapNotNull { trip ->
                        current.lines.firstOrNull { it.id == trip.lineId } ?: ScheduleLine(
                            id = trip.lineId,
                            sourceId = trip.routeId.toString(),
                            number = departures.firstOrNull { it.routeId == trip.routeId }?.lineNumber ?: trip.routeId.toString(),
                            name = trip.headsign,
                            provider = stopKey.provider.toScheduleProvider(),
                            stopIds = listOf(stopKey.toScheduleId()),
                        )
                    }.distinctBy { it.id }
                }
                mutableState.value = current.copy(
                    lines = current.lines.filterNot { old -> addedLines.any { it.id == old.id } } + addedLines,
                    departures = trips,
                    trip = current.trip?.withLiveOverlay(trips),
                    departuresState = ScheduleLoadState.Success,
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val current = mutableState.value
            if (current.stopId == requestState.stopId) {
                current.copy(
                    departuresState = ScheduleLoadState.Error(error.message ?: "Nie udało się odświeżyć odjazdów."),
                ).also { mutableState.value = it }
            }
        }
    }

    private suspend fun queryLineLiveOnce(requestState: ScheduleUiState, stopKey: TransitStopKey) {
        val line = requestState.selectedLine ?: return
        if (requestState.date != requestState.today) return
        try {
            stopsRepository.getDepartures(stopKey).collect { departures ->
                val current = mutableState.value
                if (current.lineId != requestState.lineId || current.stopId != requestState.stopId || current.date != requestState.date) return@collect
                val patched = current.departures.map { planned ->
                    val plannedTime = planned.stopTimes.firstOrNull { it.stopId == stopKey.toScheduleId() }
                        ?.let { it.departure ?: it.arrival }
                    val live = departures.firstOrNull { actual ->
                        actual.routeId == planned.routeId && actual.tripId == planned.tripId &&
                            plannedTime != null && actual.theoreticalTime == plannedTime
                    } ?: return@map planned.copy(live = false, delayMinutes = 0, cancelled = false)
                    val cancelled = live.status?.uppercase() in setOf("X", "C", "CANCELLED", "ODWOLANY")
                    val estimate = live.estimatedTime
                    val theoretical = live.theoreticalTime
                    val delay = if (estimate != null && theoretical != null) (estimate - theoretical).inWholeMinutes.toInt() else 0
                    planned.copy(delayMinutes = delay, cancelled = cancelled, live = cancelled || live.estimatedTime != null)
                }
                mutableState.value = current.copy(departures = patched, trip = current.trip?.withLiveOverlay(patched))
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            logError(LOG_TAG, "Failed to refresh live timetable for ${line.id}", error)
            val state = mutableState.value
            if (state.lineId == requestState.lineId && state.stopId == requestState.stopId) {
                mutableState.value = state.copy(
                    departures = state.departures.map { it.copy(live = false, delayMinutes = 0, cancelled = false) },
                    trip = state.trip?.copy(live = false, delayMinutes = 0, cancelled = false),
                )
            }
        }
    }

    private fun ScheduleTrip.withLiveOverlay(trips: List<ScheduleTrip>): ScheduleTrip {
        val update = trips.firstOrNull { it.id == id }
        return copy(delayMinutes = update?.delayMinutes ?: 0, live = update?.live ?: false, cancelled = update?.cancelled ?: false)
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        liveRequestJob?.cancel()
        liveRequestJob = null
        timetableJob?.cancel()
        timetableJob = null
        tripRouteJob?.cancel()
        locationJob?.cancel()
    }

    private fun retryCurrent() {
        val state = mutableState.value
        loadedTimetable = null
        if (state.providerErrors.isNotEmpty()) loadCatalogs()
        if (state.stopsState is ScheduleLoadState.Error) loadStops()
        if (state.trip != null) {
            loadTripRoute(state.trip)
        } else {
            reloadSelection()
        }
    }

    private fun loadTripRoute(trip: ScheduleTrip) {
        tripRouteJob?.cancel()
        tripRouteJob = viewModelScope.launch {
            if (trip.stopTimes.size > 1 || mutableState.value.savedScheduleId != null || trip.date != mutableState.value.today) {
                mutableState.value = mutableState.value.copy(tripState = ScheduleLoadState.Success)
                return@launch
            }
            val line = mutableState.value.line(trip.lineId)
            val provider = line.provider.toTransitProvider()
            try {
                routesRepository.getRoute(provider, trip.routeId, trip.tripId, trip.routeDepartureTime ?: trip.stopTimes.firstOrNull { it.stopId == trip.boardingStopId }?.departure)
                    .collect { route ->
                        val state = mutableState.value
                        if (state.trip?.id != trip.id) return@collect
                        val stops = route.stops.sortedBy { it.sequence }.map {
                            ScheduleStopTime(it.key.toScheduleId(), it.sequence, arrival = it.expectedTime, departure = it.expectedTime)
                        }
                        val directionId = trip.directionId.ifEmpty { trip.headsign.ifEmpty { "route:${trip.routeId}:${trip.tripId}" } }
                        val updatedTrip = trip.copy(stopTimes = stops.ifEmpty { trip.stopTimes }, directionId = directionId)
                        val existingDirections = line.directions.filterNot { it.id == directionId }
                        val routeDirection = ScheduleDirection(directionId, trip.headsign, (stops.ifEmpty { trip.stopTimes }).map { it.stopId })
                        val enrichedLine = line.copy(
                            directions = existingDirections + routeDirection,
                            stopIds = (line.stopIds + routeDirection.stopIds).distinct(),
                        )
                        mutableState.value = state.copy(
                            trip = updatedTrip,
                            lines = (state.lines.filterNot { it.id == line.id } + enrichedLine),
                            tripState = ScheduleLoadState.Success,
                        )
                    }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                logError(LOG_TAG, "Failed to load trip route ${trip.sourceTripId}", error)
                val state = mutableState.value
                if (state.trip?.id == trip.id) {
                    state.copy(tripState = ScheduleLoadState.Error(error.message ?: "Nie udało się wczytać szczegółów kursu."))
                        .also { mutableState.value = it }
                }
            }
        }
    }

    private fun loadNearbyStops() {
        locationJob?.cancel()
        if (!permissionChecker.isLocationPermissionGranted()) {
            mutableState.value = mutableState.value.copy(nearbyMessage = "Zezwól na lokalizację, aby sortować przystanki według odległości.")
            return
        }
        locationJob = viewModelScope.launch {
            try {
                locationRepository.getLocation().collect { location ->
                    val state = mutableState.value
                    if (location.isFixed) {
                        mutableState.value = state.copy(nearbyMessage = "Bieżąca platforma nie udostępnia GPS; odległości nie są wyliczane.")
                    } else {
                        val sorted = state.stops.map { stop ->
                            val data = stateStopData(stop.id) ?: return@map stop
                            stop.copy(distanceMeters = distanceMeters(location, data.stopLat, data.stopLon))
                        }
                        mutableState.value = state.copy(stops = sorted, nearbyMessage = null)
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(nearbyMessage = "Nie udało się odczytać lokalizacji.")
            }
        }
    }

    private fun updatePreferences(state: ScheduleUiState, action: ScheduleAction): ScheduleUiState {
        val preferences = state.preferences
        return when (action) {
            is ScheduleAction.ToggleFavorite -> state.copy(
                preferences = preferences.copy(
                    favorites = if (action.key in preferences.favorites) preferences.favorites - action.key else preferences.favorites + action.key,
                ),
            )
            ScheduleAction.ClearHistory -> state.copy(preferences = preferences.copy(history = emptyList()))
            ScheduleAction.SaveOffline -> saveOffline(state)
            is ScheduleAction.RemoveSaved -> state.copy(preferences = preferences.copy(saved = preferences.saved.filterNot { it.id == action.id }))
            is ScheduleAction.OpenSaved -> state.copy(
                lineId = action.saved.lineId,
                stopId = action.saved.trips.firstOrNull()?.boardingStopId ?: action.saved.lineSnapshot?.stops(action.saved.reversed)?.firstOrNull(),
                date = action.saved.date,
                directionId = action.saved.trips.firstOrNull()?.directionId,
                reversed = action.saved.reversed,
                stopSearch = false,
                savedScheduleId = action.saved.id,
                departures = action.saved.trips,
                departuresState = ScheduleLoadState.Success,
            )
            is ScheduleAction.SaveAlert -> saveAlert(state, action.alert)
            is ScheduleAction.RemoveAlert -> state.copy(preferences = preferences.copy(alerts = preferences.alerts.filterNot { it.trip.id == action.tripId }))
            else -> state
        }
    }

    private fun saveOffline(state: ScheduleUiState): ScheduleUiState {
        val line = state.selectedLine ?: return state.copy(message = "Wybierz linię przed zapisaniem rozkładu.")
        if (state.departuresState !is ScheduleLoadState.Success || state.departures.isEmpty()) {
            return state.copy(message = "Nie ma pobranego rozkładu do zapisania offline.")
        }
        var estimatedSnapshotSize = 0
        val trips = buildList {
            state.departures.forEach { trip ->
                if (size >= MAX_SAVED_TRIPS || !trip.hasPlannedBoardingTime) return@forEach
                val estimatedTripSize = 160 + trip.stopTimes.size * ESTIMATED_STOP_TIME_BYTES
                if (estimatedSnapshotSize + estimatedTripSize > MAX_SAVED_SNAPSHOT_BYTES) return@forEach
                estimatedSnapshotSize += estimatedTripSize
                add(trip.copy(live = false, delayMinutes = 0, cancelled = false))
            }
        }
        if (trips.isEmpty()) return state.copy(message = "Brak godzin planowych do zapisania offline.")
        val stops = state.routeStops + listOfNotNull(state.selectedStop)
        val snapshotLine = line.copy(
            directions = state.selectedDirection?.let { listOf(it) }.orEmpty(),
            stopIds = state.routeStops.map { it.id },
        )
        val saved = SavedSchedule(line.id, state.date, state.reversed, trips, snapshotLine, stops.distinctBy { it.id })
        val savedList = (state.preferences.saved.filterNot { it.id == saved.id } + saved)
            .takeLast(MAX_SAVED_SCHEDULES)
            .let { snapshots ->
                var remaining = snapshots
                while (remaining.size > 1 && remaining.sumOf(::estimatedSavedSize) > MAX_SAVED_SNAPSHOT_BYTES) {
                    remaining = remaining.drop(1)
                }
                remaining
            }
        return state.copy(
            preferences = state.preferences.copy(saved = savedList),
            message = if (savedList.any { it.id == saved.id }) {
                "Zapisano ${trips.size} kursów i dane przystanków offline. Opóźnienia nie są przechowywane."
            } else {
                "Migawka przekracza limit miejsca offline i nie została zapisana."
            },
        )
    }

    private fun estimatedSavedSize(saved: SavedSchedule): Int =
        256 + saved.lineSnapshot?.let { it.id.length + it.name.length + it.stopIds.sumOf(String::length) }.orZero() +
            saved.stopsSnapshot.sumOf { it.id.length + it.name.length + it.code.length + 48 } +
            saved.trips.sumOf { 160 + it.stopTimes.size * ESTIMATED_STOP_TIME_BYTES }

    private fun Int?.orZero() = this ?: 0

    private fun saveAlert(state: ScheduleUiState, alert: ScheduleAlert): ScheduleUiState {
        val trip = alert.trip
        val upcoming = trip.hasKnownBoardingTime && !trip.cancelled &&
            (trip.date > state.today || (trip.date == state.today && state.boardingMinute(trip) > state.currentMinute))
        if (!upcoming || alert.minutesBefore !in setOf(1, 3, 5, 10)) {
            return state.copy(message = "Alert wymaga przyszłego kursu z rozpoznaną godziną odjazdu.")
        }
        return state.copy(
            preferences = state.preferences.copy(alerts = (state.preferences.alerts.filterNot { it.trip.id == trip.id } + alert).takeLast(20)),
            message = "Alert zapisano w aplikacji. Systemowe powiadomienia nie są dostępne.",
        )
    }

    private fun openLine(state: ScheduleUiState, id: String) = state.line(id).let { line ->
        val stop = line.stopIds.firstOrNull()
        state.copy(
            lineId = id, stopId = stop, stopSearch = false, reversed = false,
            directionId = line.directions.firstOrNull()?.id, date = state.today,
            savedScheduleId = null, departures = emptyList(), trip = null,
            preferences = history(state, "line:$id"),
        )
    }

    private fun cycleDirection(state: ScheduleUiState): ScheduleUiState {
        val dirs = state.selectedLine?.directions.orEmpty()
        if (dirs.isEmpty()) return state.copy(message = "Kierunki pojawią się po wczytaniu rzeczywistych kursów tej linii.")
        val nextIndex = ((dirs.indexOfFirst { it.id == state.directionId }).coerceAtLeast(-1) + 1) % dirs.size
        val direction = dirs[nextIndex]
        return state.copy(
            directionId = direction.id,
            reversed = nextIndex == 1,
            stopId = if (state.stopSearch || state.selectedLine?.provider == ScheduleProvider.SKM) state.stopId else direction.stopIds.firstOrNull(),
        )
    }

    private fun selectDirection(state: ScheduleUiState, id: String): ScheduleUiState {
        val directions = state.selectedLine?.directions.orEmpty()
        val index = directions.indexOfFirst { it.id == id }
        if (index < 0) return state
        val direction = directions[index]
        return state.copy(
            directionId = direction.id,
            reversed = index == 1,
            stopId = if (state.stopSearch || state.selectedLine?.provider == ScheduleProvider.SKM) state.stopId else direction.stopIds.firstOrNull(),
        )
    }

    private fun directionIndex(state: ScheduleUiState, id: String) = state.selectedLine?.directions?.indexOfFirst { it.id == id } ?: -1

    private fun history(state: ScheduleUiState, key: String) = state.preferences.copy(
        history = (listOf(key) + state.preferences.history.filterNot { it == key }).take(MAX_HISTORY),
    )

    private fun findStopKey(id: String) = parseStopId(id)

    private fun parseStopId(id: String): TransitStopKey? {
        val provider = id.substringBefore(':').let { runCatching { TransitProvider.valueOf(it) }.getOrNull() } ?: return null
        val sourceId = id.substringAfter(':').toIntOrNull() ?: return null
        return TransitStopKey(provider, sourceId)
    }

    private fun stateStopData(id: String) = sourceStops[id]

    private fun ScheduleLine.asTimetableLine(): pl.bkacala.threecitycommuter.model.schedule.TimetableLine {
        val provider = this.provider.toTransitProvider()
        val stopKeys = stopIds.mapNotNull(::parseStopId)
        return pl.bkacala.threecitycommuter.model.schedule.TimetableLine(id, provider, sourceId, number, name, stopKeys)
    }

    private fun Departure.toScheduleTrip(stopKey: TransitStopKey, date: kotlinx.datetime.LocalDate, lines: List<ScheduleLine>): ScheduleTrip {
        val id = "${stopKey.provider.name}:$routeId"
        val line = lines.firstOrNull {
            it.id == id || (it.provider.toTransitProvider() == stopKey.provider && it.sourceId == routeId.toString()) ||
                (stopKey.provider == TransitProvider.PLK && it.provider == ScheduleProvider.SKM && it.number == lineNumber)
        }
        val scheduled = theoreticalTime
        val displayedTime = scheduled ?: estimatedTime
        val time = displayedTime?.minuteOfServiceDay(date)
        val estimate = estimatedTime
        val difference = if (estimate != null && scheduled != null) ((estimate - scheduled).inWholeMinutes).toInt() else 0
        val code = status?.uppercase()
        return ScheduleTrip(
            lineId = line?.id ?: id, date = date, startMinute = time ?: 0, reversed = false,
            boardingStopId = stopKey.toScheduleId(), delayMinutes = difference,
            cancelled = code in setOf("X", "C", "CANCELLED", "ODWOLANY"),
            live = estimatedTime != null || code in setOf("X", "C", "CANCELLED", "ODWOLANY"),
            sourceTripId = "${stopKey.provider.name}:$routeId:$tripId:${scheduled?.toEpochMilliseconds() ?: this.id}",
            routeId = routeId, tripId = tripId, directionId = "", headsign = headsign.orEmpty(),
            routeDepartureTime = scheduledTripStartTime ?: theoreticalTime,
            stopTimes = listOfNotNull(displayedTime?.let { ScheduleStopTime(stopKey.toScheduleId(), 0, departure = it) }),
            hasKnownBoardingTime = time != null,
            hasPlannedBoardingTime = scheduled != null,
        )
    }

    private fun distanceMeters(location: UserLocation, latitude: Double, longitude: Double): Int {
        val radius = 6_371_000.0
        val lat1 = location.latitude * PI / 180
        val lat2 = latitude * PI / 180
        val dLat = lat2 - lat1
        val dLon = (longitude - location.longitude) * PI / 180
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return (radius * 2 * atan2(sqrt(a), sqrt(1 - a))).roundToInt()
    }

    private companion object {
        const val LOG_TAG = "ScheduleScreenViewModel"
        const val MAX_RETAINED_STOP_TIMES = 20_000
        const val CITY_POLL_INTERVAL = 30_000L
        const val RAIL_POLL_INTERVAL = 60_000L
        const val MAX_HISTORY = 10
        const val MAX_SAVED_SCHEDULES = 12
        const val MAX_SAVED_TRIPS = 2_000
        const val ESTIMATED_STOP_TIME_BYTES = 96
        const val MAX_SAVED_SNAPSHOT_BYTES = 750_000
    }
}
