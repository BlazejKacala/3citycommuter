package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.sphericalDistance
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.transit.supportsLiveVehicleTracking
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.stops.TransitStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.common.asUiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.TransitStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.DeparturesMapper
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.TrackedVehicleMapper.mapToTrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.search.SearchResultRowModel
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase
import kotlin.time.Duration.Companion.seconds

class MapScreenViewModel(
    private val stopsRepository: TransitStopsRepository,
    private val locationRepository: LocationRepository,
    private val permissionChecker: PermissionChecker,
    private val vehiclesRepository: VehiclesRepository,
    private val getDeparturesUseCase: GetDeparturesUseCase,
    private val routesRepository: RoutesRepository,
) : ViewModel() {

    private var loadTransitStopsJob: Job? = null
    private var updateDeparturesJob: Job? = null
    private var traceUserLocationJob: Job? = null
    private var traceVehicleJob: Job? = null
    private var tracingStarted = false

    private val _uiState = MutableStateFlow(MapScreenUiState())
    private val _effects = MutableSharedFlow<MapEffect>()

    val uiState: StateFlow<MapScreenUiState> = _uiState
    val effects: SharedFlow<MapEffect> = _effects

    init {
        loadTransitStops()
        showClosestStationBoard()
    }

    fun onAction(action: MapAction) {
        when (action) {
            MapAction.ScreenResumed -> startTracingJobs()
            MapAction.ScreenPaused -> stopTracingJobs()
            MapAction.MapClicked -> clearSelection()
            MapAction.ReloadClicked -> onMapReloadRequest()
            MapAction.CenterOnUserClicked -> centerOnUserPosition()
            is MapAction.StopSelected -> selectTransitStop(action.stopKey)
            is MapAction.DepartureSelected -> onSelectDeparture(action.departureKey)
            is MapAction.SearchQueryChanged -> updateSearchQuery(action.query)
            is MapAction.SearchActiveChanged -> updateSearchActive(action.isActive)
            is MapAction.SearchResultClicked -> selectStopFromSearch(action.stopKey)
        }
    }

    private fun startTracingJobs() {
        if (tracingStarted) {
            return
        }
        tracingStarted = true
        traceUserLocation()

        if (_uiState.value.selectedTransitStop?.data?.provider?.supportsLiveVehicleTracking == true) {
            _uiState.value.selectedDeparture?.vehicleId?.let { trackVehicle(it) }
        }
        _uiState.value.selectedTransitStop?.let { updateDepartures(it) }
    }

    private fun stopTracingJobs() {
        tracingStarted = false
        traceUserLocationJob?.cancel()
        traceVehicleJob?.cancel()
        updateDeparturesJob?.cancel()
    }

    private fun traceUserLocation() {
        traceUserLocationJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (permissionChecker.isLocationPermissionGranted()) {
                    locationRepository.getLocation().collect { location ->
                        _uiState.update { it.copy(userLocation = location) }
                        refreshSearchResults()
                    }
                }
                delay(USER_LOCATION_REFRESH_INTERVAL)
            }
        }
    }

    private fun showClosestStationBoard() {
        viewModelScope.launch {
            uiState.map { it.transitStops }
                .filter { it is UiState.Success }
                .combine(uiState.map { it.userLocation }.filter { !it.isFixed }, ::Pair)
                .take(1)
                .catch { throwable ->
                    logError(LOG_TAG, "Failed while resolving the closest stop", throwable)
                    emitError()
                }
                .collect { (transitStops, userLocation) ->
                    if (transitStops is UiState.Success) {
                        val userLocationLatLng = LatLng(userLocation.latitude, userLocation.longitude)
                        val closestTransitStop =
                            transitStops.data.minByOrNull { it.position.sphericalDistance(userLocationLatLng) }
                        closestTransitStop?.let {
                            onTransitStopSelected(it)
                            _effects.emit(MapEffect.FocusCamera(it.position))
                        }
                    }
                }
        }
    }

    private fun loadTransitStops() {
        loadTransitStopsJob?.cancel()
        loadTransitStopsJob = viewModelScope.launch {
            stopsRepository.getTransitStops()
                .map { stops -> stops.map(::TransitStopMapItem) }
                .asUiState()
                .collect { state ->
                    if (state is UiState.Error) {
                        logError(LOG_TAG, "Failed to load bus stops", state.exception)
                    }
                    _uiState.update { uiState -> uiState.copy(transitStops = state) }
                    refreshSearchResults()
                }
        }
    }

    private fun selectTransitStop(stopKey: TransitStopKey) {
        currentTransitStops().firstOrNull { it.key == stopKey }?.let { onTransitStopSelected(it) }
    }

    private fun onTransitStopSelected(selected: TransitStopMapItem) {
        updateDeparturesJob?.cancel()
        traceVehicleJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTransitStop = selected,
                selectedDeparture = null,
                departures = UiState.Loading,
                route = null,
                trackedVehicle = null,
            )
        }
        updateDepartures(selected)
    }

    private fun loadRoute() {
        viewModelScope.launch {
            _uiState.value.selectedDeparture?.let { departure ->
                val provider = _uiState.value.selectedTransitStop?.data?.provider ?: return@launch
                routesRepository.getRoute(
                    provider = provider,
                    routeId = departure.routeId,
                    tripId = departure.tripId,
                ).catch { throwable ->
                    logError(
                        LOG_TAG,
                        "Failed to load route for provider=$provider routeId=${departure.routeId} tripId=${departure.tripId}",
                        throwable,
                    )
                    emitError()
                }
                    .collectLatest { route ->
                        _uiState.update { state ->
                            if (state.selectedDeparture?.departureKey != departure.departureKey) {
                                state
                            } else {
                                state.copy(
                                    route = route.shape.map { LatLng(it.latitude, it.longitude) },
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun updateDepartures(selected: TransitStopMapItem) {
        updateDeparturesJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update { state ->
                    if (state.selectedTransitStop?.key == selected.key &&
                        state.departures !is UiState.Success
                    ) {
                        state.copy(departures = UiState.Loading)
                    } else {
                        state
                    }
                }
                getDeparturesUseCase.getDepartures(selected.key)
                    .take(1)
                    .catch { throwable ->
                        logError(
                            LOG_TAG,
                            "Failed to load departures for provider=${selected.data.provider} sourceStopId=${selected.data.sourceStopId} stopName=${selected.data.name}",
                            throwable,
                        )
                        _uiState.update { state ->
                            if (state.selectedTransitStop?.key == selected.key) {
                                state.copy(departures = UiState.Error(throwable))
                            } else {
                                state
                            }
                        }
                        emitError()
                    }
                    .collect { departures ->
                        _uiState.update { state ->
                            if (state.selectedTransitStop?.key != selected.key) {
                                state
                            } else {
                                state.copy(
                                    departures = UiState.Success(DeparturesMapper.mapToBottomSheetModel(
                                        transitStopData = selected.data,
                                        departures = departures
                                            .distinctBy { departureIdentity(it.first) }
                                            .sortedBy {
                                                (it.first.estimatedTime ?: it.first.theoreticalTime)?.epochSeconds
                                                    ?: Long.MAX_VALUE
                                            }
                                            .take(MAX_DEPARTURES_DISPLAYED),
                                        selectedDepartureKey = state.selectedDeparture?.departureKey,
                                    )),
                                )
                            }
                        }
                    }
                delay(
                    if (selected.data.provider == TransitProvider.SKM) {
                        RAIL_DEPARTURES_REFRESH_INTERVAL
                    } else {
                        DEPARTURES_REFRESH_INTERVAL
                    },
                )
            }
        }
    }

    private fun onSelectDeparture(departureKey: String) {
        traceVehicleJob?.cancel()
        _uiState.update { state ->
            val departures = (state.departures as? UiState.Success)?.data
            val selectedDeparture = departures?.departures?.find {
                it.departureKey == departureKey
            }
            state.copy(
                selectedDeparture = selectedDeparture,
                trackedVehicle = null,
                route = null,
                departures = departures?.let { model ->
                    UiState.Success(
                        model.copy(
                            departures = model.departures.map {
                                it.copy(isSelected = it.departureKey == departureKey)
                            },
                        ),
                    )
                },
            )
        }

        loadRoute()
        val state = _uiState.value
        if (
            state.selectedDeparture != null &&
            state.selectedTransitStop?.data?.provider?.supportsLiveVehicleTracking == true
        ) {
            state.selectedDeparture?.vehicleId?.let { trackVehicle(it) }
        }
    }

    private fun trackVehicle(vehicleId: Long) {
        traceVehicleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val vehicleIdInt = vehicleId.toInt()
                val provider = _uiState.value.selectedTransitStop?.data?.provider ?: return@launch
                while (isActive) {
                    vehiclesRepository.getVehiclePosition(provider, vehicleIdInt)
                        .catch { throwable ->
                            logError(
                                LOG_TAG,
                                "Failed to load vehicle position for provider=$provider vehicleId=$vehicleIdInt",
                                throwable,
                            )
                            emitError()
                        }
                        .collect { vehiclePosition ->
                            vehiclePosition?.let { position ->
                                if (_uiState.value.trackedVehicle == null) {
                                    _effects.emit(
                                        MapEffect.FocusCamera(
                                            LatLng(position.lat, position.lon),
                                        ),
                                    )
                                }

                                _uiState.update { state ->
                                    val departure = state.selectedDeparture
                                    if (departure?.vehicleId != vehicleId) {
                                        state
                                    } else {
                                        state.copy(
                                            trackedVehicle = mapToTrackedVehicle(position, departure),
                                        )
                                    }
                                }
                            }
                        }
                    delay(VEHICLE_REFRESH_INTERVAL)
                }
            } catch (_: NumberFormatException) {
                emitError(INVALID_VEHICLE_ID_ERROR_MESSAGE)
            }
        }
    }

    private fun clearSelection() {
        updateDeparturesJob?.cancel()
        traceVehicleJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTransitStop = null,
                selectedDeparture = null,
                departures = null,
                route = null,
                trackedVehicle = null,
            )
        }
    }

    private fun onMapReloadRequest() {
        updateDeparturesJob?.cancel()
        loadTransitStops()
    }

    private fun centerOnUserPosition() {
        viewModelScope.launch {
            if (!_uiState.value.userLocation.isFixed) {
                _effects.emit(
                    MapEffect.FocusCamera(
                        LatLng(_uiState.value.userLocation.latitude, _uiState.value.userLocation.longitude),
                    ),
                )
            }
        }
        showClosestStationBoard()
    }

    private fun selectStopFromSearch(stopKey: TransitStopKey) {
        val station = currentTransitStops().firstOrNull { it.key == stopKey } ?: return
        _uiState.update { it.copy(isSearchActive = false) }
        onTransitStopSelected(station)
        viewModelScope.launch {
            _effects.emit(MapEffect.FocusCamera(station.position))
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshSearchResults()
    }

    private fun updateSearchActive(isActive: Boolean) {
        _uiState.update { it.copy(isSearchActive = isActive) }
        if (isActive) {
            refreshSearchResults()
        }
    }

    private fun refreshSearchResults() {
        _uiState.update {
            val transitStops = (it.transitStops as? UiState.Success)?.data ?: emptyList()
            val location = it.userLocation
            val query = it.searchQuery.lowercase()
            val searchResults = transitStops
                .filter { stop -> stop.data.name.lowercase().contains(query) }
                .map { stop ->
                    stop to stop.position.sphericalDistance(LatLng(location.latitude, location.longitude)).toInt()
                }
                .sortedBy { it.second }
                .map { (item, distance) ->
                    SearchResultRowModel(
                        stopKey = item.key,
                        station = item.data.name,
                        distance = getDistanceString(distance, location),
                        provider = item.data.provider,
                        isForBuses = item.data.isForBuses,
                        isForTrams = item.data.isForTrams,
                    )
                }
            it.copy(searchResults = searchResults)
        }
    }

    private fun currentTransitStops(): List<TransitStopMapItem> =
        (uiState.value.transitStops as? UiState.Success)?.data.orEmpty()

    private fun getDistanceString(distance: Int, userLocation: UserLocation): String {
        return if (userLocation.isFixed) {
            UNKNOWN_DISTANCE_LABEL
        } else {
            "$DISTANCE_LABEL_PREFIX ${distance}m"
        }
    }

    private suspend fun emitError(message: String = GENERIC_ERROR_MESSAGE) {
        _effects.emit(MapEffect.ShowError(message))
    }

    private fun departureIdentity(departure: pl.bkacala.threecitycommuter.model.departures.Departure): String =
        listOf(
            departure.id,
            departure.routeId.toString(),
            departure.tripId.toString(),
            departure.vehicleId?.toString().orEmpty(),
            departure.theoreticalTime?.toString().orEmpty(),
            departure.estimatedTime?.toString().orEmpty(),
            departure.headsign.orEmpty(),
        ).joinToString("|")
}

private const val LOG_TAG = "MapScreenViewModel"
private val USER_LOCATION_REFRESH_INTERVAL = 10.seconds
private val DEPARTURES_REFRESH_INTERVAL = 30.seconds
private val RAIL_DEPARTURES_REFRESH_INTERVAL = 60.seconds
private val VEHICLE_REFRESH_INTERVAL = 10.seconds
private const val MAX_DEPARTURES_DISPLAYED = 15

private const val GENERIC_ERROR_MESSAGE = "Nie udało się wczytać danych"
private const val INVALID_VEHICLE_ID_ERROR_MESSAGE = "Nieprawidłowy identyfikator pojazdu"
private const val UNKNOWN_DISTANCE_LABEL = "Odległość od przystanka nieznana, brak Twojej lokalizacji"
private const val DISTANCE_LABEL_PREFIX = "Przystanek odległy o"
