package pl.bkacala.threecitycommuter.ui.component.map

import androidx.compose.runtime.Stable
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import pl.bkacala.threecitycommuter.model.stops.BusStopData

@Stable
class BusStopClusterItem(
    private val busStopItem : BusStopData
) : ClusterItem {

    private val latLng = LatLng(busStopItem.stopLat, busStopItem.stopLon)

    override fun getPosition(): LatLng {
        return latLng
    }

    override fun getTitle(): String? {
        return busStopItem.stopName
    }

    override fun getSnippet(): String? {
        return busStopItem.stopShortName

    }

    override fun getZIndex(): Float {
       return  1f
    }
}