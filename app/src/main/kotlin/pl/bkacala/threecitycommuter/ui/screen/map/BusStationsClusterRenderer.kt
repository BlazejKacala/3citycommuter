package pl.bkacala.threecitycommuter.ui.screen.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem


@Composable
@GoogleMapComposable
@MapsComposeExperimentalApi
fun rememberBusStopsClusterRenderer(
    clusterManager: ClusterManager<BusStopMapItem>?,
    itemBitmap: BitmapDescriptor
): BusStationsClusterRenderer? {
    val context = LocalContext.current
    val clusterRendererState: MutableState<BusStationsClusterRenderer?> =
        remember { mutableStateOf(null) }

    clusterManager ?: return null
    MapEffect(context) { map ->
        val renderer = BusStationsClusterRenderer(context, map, clusterManager, itemBitmap)
        clusterRendererState.value = renderer
    }

    return clusterRendererState.value
}

class BusStationsClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<BusStopMapItem>,
    private val itemBitmap: BitmapDescriptor
) : DefaultClusterRenderer<BusStopMapItem>(
    context, map, clusterManager
) {

    override fun onBeforeClusterRendered(
        cluster: Cluster<BusStopMapItem?>,
        markerOptions: MarkerOptions
    ) {
        markerOptions.icon(getDescriptorForCluster(cluster))
    }

    override fun onClusterUpdated(cluster: Cluster<BusStopMapItem?>, marker: Marker) {
        try {
            marker.setIcon(getDescriptorForCluster(cluster))
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    override fun onClusterItemRendered(clusterItem: BusStopMapItem, marker: Marker) {
        try {
            marker.setIcon(itemBitmap)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    override fun onBeforeClusterItemRendered(
        item: BusStopMapItem,
        markerOptions: MarkerOptions
    ) {
        super.onBeforeClusterItemRendered(item, markerOptions)
    }
}