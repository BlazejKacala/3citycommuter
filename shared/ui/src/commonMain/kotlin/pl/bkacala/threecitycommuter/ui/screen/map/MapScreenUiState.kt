package pl.bkacala.threecitycommuter.ui.screen.map

import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.search.SearchResultRowModel

data class MapScreenUiState(
    val busStops: UiState<List<BusStopMapItem>> = UiState.Loading,
    val userLocation: UserLocation = UserLocation.default(),
    val selectedBusStop: BusStopMapItem? = null,
    val selectedDeparture: DepartureRowModel? = null,
    val departures: DeparturesBottomSheetModel? = null,
    val route: List<LatLng>? = null,
    val trackedVehicle: TrackedVehicle? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResultRowModel> = emptyList(),
) {
    val showCenterOnPositionButton: Boolean
        get() = !userLocation.isFixed
}
