package pl.bkacala.threecitycommuter.ui.screen.map

sealed interface MapAction {
    data object ScreenResumed : MapAction
    data object ScreenPaused : MapAction
    data object MapClicked : MapAction
    data object ReloadClicked : MapAction
    data object CenterOnUserClicked : MapAction
    data class StopSelected(val stopId: Int) : MapAction
    data class DepartureSelected(val vehicleId: Long?) : MapAction
    data class SearchQueryChanged(val query: String) : MapAction
    data class SearchActiveChanged(val isActive: Boolean) : MapAction
    data class SearchResultClicked(val stopId: Int) : MapAction
}
