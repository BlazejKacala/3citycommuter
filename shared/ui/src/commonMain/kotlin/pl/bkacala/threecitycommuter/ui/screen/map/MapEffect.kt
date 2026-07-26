package pl.bkacala.threecitycommuter.ui.screen.map

import pl.bkacala.threecitycommuter.model.LatLng

sealed interface MapEffect {
    data class ShowError(val message: String) : MapEffect
    data class FocusCamera(val target: LatLng) : MapEffect
}
