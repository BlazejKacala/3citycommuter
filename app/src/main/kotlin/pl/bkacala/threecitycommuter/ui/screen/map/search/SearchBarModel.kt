package pl.bkacala.threecitycommuter.ui.screen.map.search

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.ktx.utils.sphericalDistance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem

class SearchBarModel(
    private val _isActive: MutableStateFlow<Boolean>,
    private val _query: MutableStateFlow<String>,
    private val _results: MutableStateFlow<List<SearchResultRowModel>>,
    private val busStops: MutableStateFlow<UiState<List<BusStopMapItem>>>,
    private val userLocation: MutableStateFlow<UserLocation>,
    private val scope: CoroutineScope,
    private val onResultClicked: (id: Int) -> Unit
) {
    val isActive: StateFlow<Boolean> = _isActive
    val query: StateFlow<String> = _query
    val results: StateFlow<List<SearchResultRowModel>> = _results

    fun onQueryChanged(query: String) {
        _query.value = query
        updateResults(query)
    }

    private fun updateResults(query: String) {
        scope.launch {
            if (busStops.value is UiState.Success) {
                val busStops = (busStops.value as UiState.Success<List<BusStopMapItem>>).data
                val location = userLocation.value

                _results.value = busStops.filter {
                    it.data.name.lowercase().contains(query.lowercase())
                }.map {
                    Pair(
                        it,
                        it.position.sphericalDistance(LatLng(location.latitude, location.longitude))
                            .toInt()
                    )
                }.sortedBy { it.second }.map {
                    val (item, distance) = it
                    SearchResultRowModel(
                        station = item.data.name,
                        distance = getDistanceString(distance, location),
                        isForBuses = item.data.isForBuses,
                        isForTrams = item.data.isForTrams,
                        onClicked = {
                            _isActive.value = false
                            onResultClicked(item.id)
                        }
                    )
                }
            }
        }
    }

    private fun getDistanceString(distance: Int, userLocation: UserLocation): String {
        return if (userLocation.isFixed)
            "Odległość od przystanka nieznana, brak Twojej lokalizacji"
        else
            "Przystanek odległy o ${distance}m"
    }

    fun onSearchBarActiveChange() {
        _isActive.value = !_isActive.value
        if (_isActive.value) {
            updateResults(_query.value)
        }
    }

}

