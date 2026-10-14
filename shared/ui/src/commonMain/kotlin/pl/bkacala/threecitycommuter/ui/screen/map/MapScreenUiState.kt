package pl.bkacala.threecitycommuter.ui.screen.map

import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.component.TransitStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.search.SearchResultRowModel

data class MapScreenUiState(
    val transitStops: UiState<List<TransitStopMapItem>> = UiState.Loading,
    val userLocation: UserLocation = UserLocation.default(),
    val selectedTransitStop: TransitStopMapItem? = null,
    val selectedDeparture: DepartureRowModel? = null,
    val routeLoadingDepartureKey: String? = null,
    val departures: UiState<DeparturesBottomSheetModel>? = null,
    val route: List<LatLng>? = null,
    val routeStops: List<Route.Stop> = emptyList(),
    val routeTimetable: Route? = null,
    val vehiclePosition: VehiclePosition? = null,
    val routeProgress: RouteProgress = RouteProgress(),
    val currentRouteStopKey: TransitStopKey? = null,
    val trackedVehicle: TrackedVehicle? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResultRowModel> = emptyList(),
) {
    val showCenterOnPositionButton: Boolean
        get() = !userLocation.isFixed
}
