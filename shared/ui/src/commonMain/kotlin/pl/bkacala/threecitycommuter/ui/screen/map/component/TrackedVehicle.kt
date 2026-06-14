package pl.bkacala.threecitycommuter.ui.screen.map.component

import pl.bkacala.threecitycommuter.model.LatLng

enum class GpsQuality {
    NoSignal,
    Weak,
    Strong,
}

data class TrackedVehicle(
    val type: VehicleType,
    val delay: String,
    val number: String,
    val gpsQuality: GpsQuality,
    val position: LatLng,
)
