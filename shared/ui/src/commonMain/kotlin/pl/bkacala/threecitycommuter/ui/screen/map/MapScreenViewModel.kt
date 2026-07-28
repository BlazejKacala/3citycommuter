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
import pl.bkacala.threecitycommuter.model.transit.supportsLiveVehicleTracking
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.common.asUiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.DeparturesMapper
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.TrackedVehicleMapper.mapToTrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.search.SearchResultRowModel
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase
import kotlin.time.Duration.Companion.seconds

class MapScreenViewModel(
    private val stopsRepository: BusStopsRepository,
    private val locationRepository: LocationRepository,
    private val permissionChecker: PermissionChecker,
    private val vehiclesRepository: VehiclesRepository,
    private val getDeparturesUseCase: GetDeparturesUseCase,
    private val routesRepository: RoutesRepository,
) : ViewModel() {

    private var loadBusStopsJob: Job? = null
    private var updateDeparturesJob: Job? = null
    private var traceUserLocationJob: Job? = null
    private var traceVehicleJob: Job? = null
    private var tracingStarted = false

    private val _uiState = MutableStateFlow(MapScreenUiState())
    private val _effects = MutableSharedFlow<MapEffect>()

    val uiState: StateFlow<MapScreenUiState> = _uiState
    val effects: SharedFlow<MapEffect> = _effects

    init {
        loadBusStops()
        showClosestStationBoard()
    }

    fun onAction(action: MapAction) {
        when (action) {
            MapAction.ScreenResumed -> startTracingJobs()
            MapAction.ScreenPaused -> stopTracingJobs()
            MapAction.MapClicked -> clearSelection()
            MapAction.ReloadClicked -> onMapReloadRequest()
            MapAction.CenterOnUserClicked -> centerOnUserPosition()
            is MapAction.StopSelected -> selectBusStop(action.stopId)
            is MapAction.DepartureSelected -> onSelectDeparture(action.departureKey)
            is MapAction.SearchQueryChanged -> updateSearchQuery(action.query)
            is MapAction.SearchActiveChanged -> updateSearchActive(action.isActive)
            is MapAction.SearchResultClicked -> selectStopFromSearch(action.stopId)
        }
    }

    private fun startTracingJobs() {
        if (tracingStarted) {
            return
        }
        tracingStarted = true
        traceUserLocation()

        if (_uiState.value.selectedBusStop?.data?.provider?.supportsLiveVehicleTracking == true) {
            _uiState.value.selectedDeparture?.vehicleId?.let { trackVehicle(it) }
        }
        _uiState.value.selectedBusStop?.let { updateDepartures(it) }
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
            uiState.map { it.busStops }
                .filter { it is UiState.Success }
                .combine(uiState.map { it.userLocation }.filter { !it.isFixed }, ::Pair)
                .take(1)
                .catch { throwable ->
                    logError(LOG_TAG, "Failed while resolving the closest stop", throwable)
                    emitError()
                }
                .collect { (busStops, userLocation) ->
                    if (busStops is UiState.Success) {
                        val userLocationLatLng = LatLng(userLocation.latitude, userLocation.longitude)
                        val closestBusStop =
                            busStops.data.minByOrNull { it.position.sphericalDistance(userLocationLatLng) }
                        closestBusStop?.let {
                            onBusStopSelected(it)
                            _effects.emit(MapEffect.FocusCamera(it.position))
                        }
                    }
                }
        }
    }

    private fun loadBusStops() {
        loadBusStopsJob?.cancel()
        loadBusStopsJob = viewModelScope.launch {
            stopsRepository.getBusStops()
                .map { stops -> stops.map(::BusStopMapItem) }
                .asUiState()
                .collect { state ->
                    if (state is UiState.Error) {
                        logError(LOG_TAG, "Failed to load bus stops", state.exception)
                    }
                    _uiState.update { uiState -> uiState.copy(busStops = state) }
                    refreshSearchResults()
                }
        }
    }

    private fun selectBusStop(stopId: Int) {
        currentBusStops().firstOrNull { it.id == stopId }?.let { onBusStopSelected(it) }
    }

    private fun onBusStopSelected(selected: BusStopMapItem) {
        updateDeparturesJob?.cancel()
        traceVehicleJob?.cancel()
        _uiState.update {
            it.copy(
                selectedBusStop = selected,
                selectedDeparture = null,
                departures = null,
                route = null,
                trackedVehicle = null,
            )
        }
        updateDepartures(selected)
    }

    private fun loadRoute() {
        viewModelScope.launch {
            _uiState.value.selectedDeparture?.let { departure ->
                val provider = _uiState.value.selectedBusStop?.data?.provider ?: return@launch
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

    private fun updateDepartures(selected: BusStopMapItem) {
        updateDeparturesJob = viewModelScope.launch {
            while (isActive) {
                getDeparturesUseCase.getDepartures(selected.id)
                    .take(1)
                    .catch { throwable ->
                        logError(
                            LOG_TAG,
                            "Failed to load departures for stopId=${selected.id} stopName=${selected.data.name}",
                            throwable,
                        )
                        emitError()
                    }
                    .collect { departures ->
                        _uiState.update { state ->
                            if (state.selectedBusStop?.id != selected.id) {
                                state
                            } else {
                                state.copy(
                                    departures = DeparturesMapper.mapToBottomSheetModel(
                                        busStopData = selected.data,
                                        departures = departures.distinctBy { departureIdentity(it.first) },
                                        selectedDepartureKey = state.selectedDeparture?.departureKey,
                                    ),
                                )
                            }
                        }
                    }
                delay(DEPARTURES_REFRESH_INTERVAL)
            }
        }
    }

    private fun onSelectDeparture(departureKey: String) {
        traceVehicleJob?.cancel()
        _uiState.update { state ->
            val selectedDeparture = state.departures?.departures?.find {
                it.departureKey == departureKey
            }
            state.copy(
                selectedDeparture = selectedDeparture,
                trackedVehicle = null,
                route = null,
                departures = state.departures?.copy(
                    departures = state.departures.departures.map {
                        it.copy(isSelected = it.departureKey == departureKey)
                    },
                ),
            )
        }

        loadRoute()
        val state = _uiState.value
        if (
            state.selectedDeparture != null &&
            state.selectedBusStop?.data?.provider?.supportsLiveVehicleTracking == true
        ) {
            state.selectedDeparture?.vehicleId?.let { trackVehicle(it) }
        }
    }

    private fun trackVehicle(vehicleId: Long) {
        traceVehicleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val vehicleIdInt = vehicleId.toInt()
                val provider = _uiState.value.selectedBusStop?.data?.provider ?: return@launch
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
                selectedBusStop = null,
                selectedDeparture = null,
                departures = null,
                route = null,
                trackedVehicle = null,
            )
        }
    }

    private fun onMapReloadRequest() {
        updateDeparturesJob?.cancel()
        loadBusStops()
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

    private fun selectStopFromSearch(stopId: Int) {
        val station = currentBusStops().firstOrNull { it.id == stopId } ?: return
        _uiState.update { it.copy(isSearchActive = false) }
        onBusStopSelected(station)
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
            val busStops = (it.busStops as? UiState.Success)?.data ?: emptyList()
            val location = it.userLocation
            val query = it.searchQuery.lowercase()
            it.copy(
                searchResults = busStops
                    .filter { stop -> stop.data.name.lowercase().contains(query) }
                    .map { stop ->
                        stop to stop.position.sphericalDistance(LatLng(location.latitude, location.longitude)).toInt()
                    }
                    .sortedBy { it.second }
                    .map { (item, distance) ->
                        SearchResultRowModel(
                            stopId = item.id,
                            station = item.data.name,
                            distance = getDistanceString(distance, location),
                            isForBuses = item.data.isForBuses,
                            isForTrams = item.data.isForTrams,
                        )
                    },
            )
        }
    }

    private fun currentBusStops(): List<BusStopMapItem> =
        (uiState.value.busStops as? UiState.Success)?.data.orEmpty()

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
private val VEHICLE_REFRESH_INTERVAL = 10.seconds

private const val GENERIC_ERROR_MESSAGE = "Nie udało się wczytać danych"
private const val INVALID_VEHICLE_ID_ERROR_MESSAGE = "Nieprawidłowy identyfikator pojazdu"
private const val UNKNOWN_DISTANCE_LABEL = "Odległość od przystanka nieznana, brak Twojej lokalizacji"
private const val DISTANCE_LABEL_PREFIX = "Przystanek odległy o"
