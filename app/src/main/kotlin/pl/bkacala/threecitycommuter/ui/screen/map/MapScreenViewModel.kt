package pl.bkacala.threecitycommuter.ui.screen.map

import android.Manifest
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.ktx.utils.sphericalDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.common.asUiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.DeparturesMapper
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.TrackedVehicleMapper.mapToTrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.search.SearchBarModel
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase
import pl.bkacala.threecitycommuter.utils.stateInViewModelScope
import javax.inject.Inject

@HiltViewModel
class MapScreenViewModel
    @Inject
    constructor(
        private val stopsRepository: BusStopsRepository,
        private val locationRepository: LocationRepository,
        private val permissionFlow: PermissionFlow,
        private val vehiclesRepository: VehiclesRepository,
        private val getDeparturesUseCase: GetDeparturesUseCase,
    ) : ViewModel() {

    private var updateDeparturesJob: Job? = null
    private var traceUserLocationJob: Job? = null
    private var traceVehicleJob: Job? = null

    private val _location = MutableStateFlow(UserLocation.default())
    private val _departures = MutableStateFlow<DeparturesBottomSheetModel?>(null)
    private val _busStops = MutableStateFlow<UiState<List<BusStopMapItem>>>(UiState.Loading)
    private val _selectedBusStop = MutableStateFlow<BusStopMapItem?>(null)
    private val _selectedDeparture = MutableStateFlow<DepartureRowModel?>(null)
    private val _cameraFocusFlow = MutableSharedFlow<LatLng?>(0)
    private val _trackedVehicle = MutableStateFlow<TrackedVehicle?>(null)
    private val _errorFlow = MutableSharedFlow<Throwable>()

    val location: StateFlow<UserLocation> = _location
    val departures: StateFlow<DeparturesBottomSheetModel?> = _departures
    val busStops: StateFlow<UiState<List<BusStopMapItem>>> = _busStops
    val selectedBusStop: StateFlow<BusStopMapItem?> = _selectedBusStop
    val trackedVehicle: StateFlow<TrackedVehicle?> = _trackedVehicle
    val errors: SharedFlow<Throwable> = _errorFlow

    val centerOnPositionVisibility = _location
        .map { !it.isFixed }
        .stateInViewModelScope(this, initialValue = false)

    val cameraPosition = merge(_cameraFocusFlow, location.take(2).map { LatLng(it.latitude, it.longitude) })
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val searchBarModel = SearchBarModel(
        _isActive = MutableStateFlow(false),
        _query = MutableStateFlow(""),
        _results = MutableStateFlow(listOf()),
        busStops = _busStops,
        userLocation = _location,
        scope = viewModelScope,
        onResultClicked = { id ->
            if (_busStops.value is UiState.Success) {
                val station = (_busStops.value as UiState.Success<List<BusStopMapItem>>)
                    .data.first { it.id == id }
                onBusStopSelected(station)
                viewModelScope.launch {
                    _cameraFocusFlow.emit(station.position)
                }
            }
        }
    )

    init {
        loadBusStops()
        showClosestStationBoard()
    }

    fun stopTracingJobs() {
        traceUserLocationJob?.cancel()
        traceVehicleJob?.cancel()
        updateDeparturesJob?.cancel()
    }

    fun startTracingJobs() {
        traceUserLocation()

        val selectedDeparture = _selectedDeparture.value
        if (selectedDeparture?.vehicleId != null) {
            trackVehicle(selectedDeparture.vehicleId)
        }

        val selectedBusStop = _selectedBusStop.value
        if (selectedBusStop != null) {
            updateDepartures(selectedBusStop)
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun traceUserLocation() {
        traceUserLocationJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                permissionFlow.getPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
                    .filter { it.isGranted }.flatMapLatest {
                        locationRepository.getLocation()
                    }.collect {
                        _location.emit(it)
                    }
                delay(1000 * 10)
            }
        }
    }

    private fun showClosestStationBoard() {
        viewModelScope.launch {
            busStops.filter { it is UiState.Success }
                .combine(location.filter { !it.isFixed }, ::Pair)
                .take(1)
                .catch { _errorFlow.emit(it) }
                .collect { pair ->
                    val (busStops, userLocation) = pair
                    require(busStops is UiState.Success)
                    val userLocationLatLng = LatLng(userLocation.latitude, userLocation.longitude)
                    val closestBusStop =
                        busStops.data.minByOrNull { it.position.sphericalDistance(userLocationLatLng) }
                    closestBusStop?.let {
                        onBusStopSelected(it)
                    }
                }
        }
    }

    private fun loadBusStops() {
        viewModelScope.launch {
            stopsRepository.getBusStops().map {
                it.map { busStopData -> BusStopMapItem(busStopData) }
            }.asUiState()
                .collect {
                    _busStops.value = it
                }
        }
    }

    fun onBusStopSelected(selected: BusStopMapItem) {
        _trackedVehicle.value = null
        _selectedDeparture.value = null
        updateDeparturesJob?.cancel()
        traceVehicleJob?.cancel()
        _selectedBusStop.value = selected
        updateDepartures(selected)
    }

    private fun updateDepartures(selected: BusStopMapItem) {
        updateDeparturesJob = viewModelScope.launch {
            while (isActive) {
                getDeparturesUseCase.getDepartures(selected.id)
                    .take(1)
                    .catch { _errorFlow.emit(it) }
                    .collect { departures ->
                        _departures.value =
                            DeparturesMapper.mapToBottomSheetModel(
                                busStopData = selected.data,
                                departures = departures.distinctBy { it.first.vehicleId },
                                selectedDeparture = _selectedDeparture.value,
                                onSelected = { onSelectDeparture(it) }
                            )
                    }
                delay(1000 * 30)
            }
        }
    }

    private fun onSelectDeparture(vehicleId: Long?) {
        traceVehicleJob?.cancel()
        _trackedVehicle.value = null

        _selectedDeparture.value = departures.value?.departures?.first {
            vehicleId != null && it.vehicleId == vehicleId
        }
        departures.value?.departures?.fastForEach {
            it.isSelected.value = false
        }
        _selectedDeparture.value?.isSelected?.value = true

        vehicleId?.let { trackVehicle(it) }
    }

    private fun trackVehicle(vehicleId: Long) {
        traceVehicleJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                vehiclesRepository.getVehiclePosition(vehicleId.toInt())
                    .catch { _errorFlow.emit(it) }
                    .collect {
                        it?.let { vehiclePosition ->
                            if (_trackedVehicle.value == null) {
                                _cameraFocusFlow.emit(
                                    LatLng(
                                        vehiclePosition.lat,
                                        vehiclePosition.lon
                                    )
                                )
                            }
                            if (_selectedDeparture.value != null) {
                                _trackedVehicle.emit(
                                    mapToTrackedVehicle(vehiclePosition, _selectedDeparture.value!!)
                                )
                            }
                        }
                    }
                delay(1000 * 10)
            }
        }
    }

    fun onMapClicked() {
        _trackedVehicle.value = null
        _selectedBusStop.value = null
        _selectedDeparture.value = null
        _departures.value = null
        updateDeparturesJob?.cancel()
        traceVehicleJob?.cancel()
    }

    fun onMapReloadRequest() {
        updateDeparturesJob?.cancel()
        viewModelScope.launch {
            loadBusStops()
        }
    }

    fun centerOnUserPosition() {
        viewModelScope.launch {
            if (!_location.value.isFixed) {
                _cameraFocusFlow.emit(LatLng(_location.value.latitude, _location.value.longitude))
            }
        }
        showClosestStationBoard()

    }
}
