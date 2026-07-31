package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.runtime.Stable
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

@Stable
class BusStopMapItem(
    busStopItem: BusStopData,
) {

    enum class Type {
        Bus,
        Tram,
        Both,
        Train,
    }

    val position = LatLng(busStopItem.stopLat, busStopItem.stopLon)

    val key: TransitStopKey = busStopItem.stopKey

    val data = busStopItem

    fun getStationType(): Type {
        return if (data.provider == TransitProvider.SKM) {
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
