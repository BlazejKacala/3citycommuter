package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.common.asUiState
import pl.bkacala.threecitycommuter.ui.component.map.BusStopClusterItem
import pl.bkacala.threecitycommuter.utils.stateInViewModelScope
import javax.inject.Inject

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val stopsRepository: BusStopsRepository
) : ViewModel() {

    val busStops = stopsRepository.getBusStops().map {
        it.map { busStopData -> BusStopClusterItem(busStopData) }
    }
        .asUiState()
        .stateInViewModelScope(
        this,
        initialValue = UiState.Loading
    )

}