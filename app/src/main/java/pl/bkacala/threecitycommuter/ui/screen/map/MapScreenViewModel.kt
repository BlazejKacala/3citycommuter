package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import pl.bkacala.threecitycommuter.model.stops.StopData
import pl.bkacala.threecitycommuter.repository.stops.StopsRepository
import javax.inject.Inject

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val stopsRepository: StopsRepository
) : ViewModel() {

    private val busStopsFlow = flow<List<StopData>> { stopsRepository.getBusStops() }
    val busStop: StateFlow<List<StopData>> =
        busStopsFlow.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
}