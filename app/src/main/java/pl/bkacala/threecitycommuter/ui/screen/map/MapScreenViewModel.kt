package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.common.asUiState
import pl.bkacala.threecitycommuter.ui.component.map.BusStopClusterItem
import pl.bkacala.threecitycommuter.utils.stateInViewModelScope
import javax.inject.Inject

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    stopsRepository: BusStopsRepository,
    locationRepository: LocationRepository
) : ViewModel() {

    val busStops = stopsRepository.getBusStops().map {
        it.map { busStopData -> BusStopClusterItem(busStopData) }
    }
        .asUiState()
        .stateInViewModelScope(
        this,
        initialValue = UiState.Loading
    )

    val location = locationRepository.getLocation().stateInViewModelScope(
        this,
        initialValue = UserLocation.default()
    )

}