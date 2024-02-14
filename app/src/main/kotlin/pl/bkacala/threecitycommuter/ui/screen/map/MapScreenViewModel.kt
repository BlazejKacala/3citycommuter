package pl.bkacala.threecitycommuter.ui.screen.map

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.ktx.utils.sphericalDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.common.asUiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.DeparturesMapper
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
        private val getDeparturesUseCase: GetDeparturesUseCase,
    ) : ViewModel() {

    private var updateDeparturesJob: Job? = null
    private var traceUserLocationJob: Job? = null

    private val _location = MutableStateFlow(UserLocation.default())
    private val _departures = MutableStateFlow<DeparturesBottomSheetModel?>(null)
    private val _busStops = MutableStateFlow<UiState<List<BusStopMapItem>>>(UiState.Loading)
    private val _selectedBusStop = MutableStateFlow<BusStopMapItem?>(null)

    val location: StateFlow<UserLocation> = _location
    val departures: StateFlow<DeparturesBottomSheetModel?> = _departures
    val busStops: StateFlow<UiState<List<BusStopMapItem>>> = _busStops
    val selectedBusStop: StateFlow<BusStopMapItem?> = _selectedBusStop
    val cameraPosition = _location
        .filter { !it.isFixed }
        .take(1)
        .stateInViewModelScope(this, initialValue = null)

    init {
        loadBusStops()
        showClosestStationBoard()
    }

    private fun showClosestStationBoard() {
        viewModelScope.launch {
            busStops.filter { it is UiState.Success }
                .combine(location.filter { !it.isFixed }, ::Pair)
                .take(1)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun traceUserLocation() {
        traceUserLocationJob = viewModelScope.launch {
            while (isActive) {
                permissionFlow.getPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
                    .filter { it.isGranted }.flatMapLatest {
                        locationRepository.getLocation()
                    }.collect {
                        locationRepository.getLocation().collectLatest { userLocation ->
                            _location.value = userLocation
                        }
                    }
                delay(1000 * 10)
            }
        }
    }

    fun stopTracingUserLocation() {
        traceUserLocationJob?.cancel()
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
        updateDeparturesJob?.cancel()
        _selectedBusStop.value = selected
        updateDeparturesJob = viewModelScope.launch {
            while (isActive) {
                getDeparturesUseCase.getDepartures(selected.id)
                    .take(1)
                    .collect { departures ->
                        _departures.value =
                            DeparturesMapper.mapToBottomSheetModel(
                                busStopData = selected.data,
                                departures = departures
                            )
                    }
                delay(1000 * 30)
            }
        }

    }

    fun onMapClicked() {
        _selectedBusStop.value = null
        _departures.value = null
        updateDeparturesJob?.cancel()
    }

    fun onMapReloadRequest() {
        updateDeparturesJob?.cancel()
        viewModelScope.launch {
            loadBusStops()
        }
    }
}
