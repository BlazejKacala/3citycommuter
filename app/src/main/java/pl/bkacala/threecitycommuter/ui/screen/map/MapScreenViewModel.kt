package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import pl.bkacala.threecitycommuter.model.stops.StopData
import pl.bkacala.threecitycommuter.repository.stops.StopsRepository
import pl.bkacala.threecitycommuter.utils.stateInViewModelScope
import javax.inject.Inject

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val stopsRepository: StopsRepository
) : ViewModel() {

    val busStop: StateFlow<List<StopData>> =
        stopsRepository.getBusStops().stateInViewModelScope(
            this,
                initialValue = emptyList(),
            )
}