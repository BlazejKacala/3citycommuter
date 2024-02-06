package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.algo.NonHierarchicalViewBasedAlgorithm
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.clustering.rememberClusterRenderer
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun BusStops(
    busStops: List<BusStopMapItem>,
    selectedBusStop: BusStopMapItem?,
    onClusterItemClick: (busStation: BusStopMapItem) -> Unit,
    onClusterClick: () -> Unit,

    ) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val clusterManager = rememberClusterManager<BusStopMapItem>()


    clusterManager?.setAlgorithm(
        NonHierarchicalViewBasedAlgorithm(
            screenHeight.value.toInt(),
            screenWidth.value.toInt()
        )
    )
    val renderer = rememberClusterRenderer(
        clusterContent = { cluster ->
            Cluster(it = cluster)
        },
        clusterItemContent = {
            BusStationIcon(isSelected = it.id == selectedBusStop?.id)
        },
        clusterManager = clusterManager,
    )
    SideEffect {
        clusterManager ?: return@SideEffect
        clusterManager.setOnClusterClickListener {
            onClusterClick()
            true
        }
        clusterManager.setOnClusterItemClickListener {
            onClusterItemClick(it)
            true
        }
    }
    SideEffect {
        if (clusterManager?.renderer != renderer) {
            clusterManager?.renderer = renderer ?: return@SideEffect
        }
    }
    if (clusterManager != null) {
        Clustering(
            items = busStops,
            clusterManager = clusterManager,
        )
    }
}

@Composable
private fun Cluster(it: Cluster<BusStopMapItem>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "( ${it.size} )",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        BusStationIcon(isSelected = false)
    }
}

@Composable
private fun BusStationIcon(isSelected: Boolean) {
    Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.bus_station),
        contentDescription = "Przystanek",
        tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    )
}