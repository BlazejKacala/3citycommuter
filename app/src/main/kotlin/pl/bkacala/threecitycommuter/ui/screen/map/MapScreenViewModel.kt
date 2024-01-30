package pl.bkacala.threecitycommuter.ui.screen.map

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
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
import pl.bkacala.threecitycommuter.utils.stateInViewModelScope
import javax.inject.Inject

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val stopsRepository: BusStopsRepository,
    locationRepository: LocationRepository,
    permissionFlow: PermissionFlow
) : ViewModel() {

    val location = MutableStateFlow(UserLocation.default())
    val departures = MutableStateFlow<List<DepartureRowModel>>(emptyList())
    val busStops = busStopsStateFlow(stopsRepository)

    init {
        viewModelScope.launch {
            permissionFlow.getPermissionState(Manifest.permission.ACCESS_FINE_LOCATION).collect {
                if(it.isGranted) {
                    locationRepository.getLocation().collectLatest { userLocation ->
                        location.value = userLocation
                    }
                }
            }
        }
    }

    private fun busStopsStateFlow(stopsRepository: BusStopsRepository) =
        stopsRepository.getBusStops().map {
            it.map { busStopData -> BusStopMapItem(busStopData) }
        }
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



}