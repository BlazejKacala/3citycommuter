package pl.bkacala.threecitycommuter.ui.screen.map

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.ClusterRenderer
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.ui.component.map.BusStopClusterItem


@Composable
@GoogleMapComposable
@MapsComposeExperimentalApi
public fun rememberBusStopsClusterRenderer(
    clusterManager: ClusterManager<BusStopClusterItem>?,
): MapClusterRenderer? {
    val context = LocalContext.current
    val clusterRendererState: MutableState<MapClusterRenderer?> = remember { mutableStateOf(null) }

    clusterManager ?: return null
    MapEffect(context) { map ->
        val renderer = MapClusterRenderer(context, map, clusterManager)
        clusterRendererState.value = renderer
    }

    return clusterRendererState.value
}

class MapClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<BusStopClusterItem>,
    ): DefaultClusterRenderer<BusStopClusterItem>(
    context, map, clusterManager
) {
    private val iconBitmap = ContextCompat.getDrawable(context, R.drawable.bus_station)?.toBitmap()
    private val icon = BitmapDescriptorFactory.fromBitmap(iconBitmap!!)

    override fun onBeforeClusterRendered(cluster: Cluster<BusStopClusterItem?>, markerOptions: MarkerOptions) {
        markerOptions.icon(getDescriptorForCluster(cluster))
    }

    override fun onClusterUpdated(cluster: Cluster<BusStopClusterItem?>, marker: Marker) {
        marker.setIcon(getDescriptorForCluster(cluster))
    }

    override fun onClusterItemRendered(clusterItem: BusStopClusterItem, marker: Marker) {
        marker.setIcon(icon)
    }
}