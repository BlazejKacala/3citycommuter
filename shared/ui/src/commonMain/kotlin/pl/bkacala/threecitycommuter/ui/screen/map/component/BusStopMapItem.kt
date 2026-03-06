package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.runtime.Stable
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.stops.BusStopData

@Stable
class BusStopMapItem(
    busStopItem: BusStopData
) {

    enum class Type {
        Bus, Tram, Both
    }

    val position = LatLng(busStopItem.stopLat, busStopItem.stopLon)

    val id = busStopItem.stopId

    val data = busStopItem

    fun getStationType(): Type {
        return if (data.isForBuses && data.isForTrams) {
            Type.Both
        } else if (data.isForTrams) {
            Type.Tram
        } else {
            Type.Bus
        }
    }
}
