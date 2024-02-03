package pl.bkacala.threecitycommuter.ui.screen.map

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.common.asUiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.mapper.DeparturesMapper.mapToUiRow
import pl.bkacala.threecitycommuter.utils.EmitRequest
import pl.bkacala.threecitycommuter.utils.stateInViewModelScope
import javax.inject.Inject

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val stopsRepository: BusStopsRepository,
    locationRepository: LocationRepository,
    permissionFlow: PermissionFlow
) : ViewModel() {

    private val mapBounds = MutableSharedFlow<LatLngBounds>()
    private val loadBusStateFlowRequest = MutableStateFlow(EmitRequest())

    val location = MutableStateFlow(UserLocation.default())
    val departures = MutableStateFlow<List<DepartureRowModel>>(emptyList())
    val busStops = busStopsStateFlow(stopsRepository)

    init {
        viewModelScope.launch {
            permissionFlow.getPermissionState(Manifest.permission.ACCESS_FINE_LOCATION).collect {
                if (it.isGranted) {
                    locationRepository.getLocation().collectLatest { userLocation ->
                        location.value = userLocation
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun busStopsStateFlow(stopsRepository: BusStopsRepository) =
        loadBusStateFlowRequest.flatMapLatest {
            stopsRepository.getBusStops().combine(mapBounds, ::Pair)
        }
            .map {
                val (busStops, bounds) = it
                busStops.filter { busStop ->
                    bounds.contains(LatLng(busStop.stopLat, busStop.stopLon))
                }
            }.map { it.map { busStopData -> BusStopMapItem(busStopData) } }
            .asUiState()
            .stateInViewModelScope(
                this,
                initialValue = UiState.Loading
            )


    fun onBusStopSelected(selected: BusStopMapItem) {
        viewModelScope.launch {
            stopsRepository.getDepartures(selected.id).collect { departures ->
                this@MapScreenViewModel.departures.value = departures.map { it.mapToUiRow() }
            }
        }
    }

    fun onMapClicked() {
        departures.value = emptyList()
    }

    fun onMapMoved(bounds: LatLngBounds) {
        viewModelScope.launch {
            mapBounds.emit(bounds)
        }
    }

    fun onMapReloadRequest() {
        viewModelScope.launch {
            loadBusStateFlowRequest.emit(EmitRequest())
        }
    }


}