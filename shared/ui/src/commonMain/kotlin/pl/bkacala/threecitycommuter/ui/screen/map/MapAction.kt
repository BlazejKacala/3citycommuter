package pl.bkacala.threecitycommuter.ui.screen.map

import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

sealed interface MapAction {
    data object ScreenResumed : MapAction
    data object ScreenPaused : MapAction
    data object MapClicked : MapAction
    data object ReloadClicked : MapAction
    data object CenterOnUserClicked : MapAction
    data class StopSelected(val stopKey: TransitStopKey) : MapAction
    data class DepartureSelected(val departureKey: String) : MapAction
    data class SearchQueryChanged(val query: String) : MapAction
    data class SearchActiveChanged(val isActive: Boolean) : MapAction
    data class SearchResultClicked(val stopKey: TransitStopKey) : MapAction
}
