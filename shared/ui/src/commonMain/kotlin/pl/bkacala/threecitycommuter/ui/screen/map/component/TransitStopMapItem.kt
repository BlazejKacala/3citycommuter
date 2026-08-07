package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.runtime.Stable
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

@Stable
class TransitStopMapItem(
    transitStopItem: TransitStopData,
) {

    enum class Type {
        Bus,
        Tram,
        Both,
        Train,
    }

    val position = LatLng(transitStopItem.stopLat, transitStopItem.stopLon)

    val key: TransitStopKey = transitStopItem.stopKey

    val data = transitStopItem

    fun getStationType(): Type {
        return if (data.provider == TransitProvider.PLK) {
            Type.Train
        } else if (data.isForBuses && data.isForTrams) {
            Type.Both
        } else if (data.isForTrams) {
            Type.Tram
        } else {
            Type.Bus
        }
    }
}
