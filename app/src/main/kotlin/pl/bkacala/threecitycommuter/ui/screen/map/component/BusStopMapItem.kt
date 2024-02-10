package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.runtime.Stable
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import pl.bkacala.threecitycommuter.model.stops.BusStopData

@Stable
class BusStopMapItem(
    busStopItem: BusStopData
) : ClusterItem {

    private val latLng = LatLng(busStopItem.stopLat, busStopItem.stopLon)

    override fun getPosition(): LatLng {
        return latLng
    }

    override fun getTitle(): String? {
        return null
    }

    override fun getSnippet(): String? {
        return null

    }

    override fun getZIndex(): Float {
        return 1f
    }

    val id = busStopItem.stopId

    val data = busStopItem
}