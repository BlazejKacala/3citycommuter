package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.algo.NonHierarchicalViewBasedAlgorithm
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.clustering.rememberClusterRenderer
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun BusStops(
    busStops: List<BusStopMapItem>,
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
            StationIcon(it.getStationType(), isSelected = false)
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
    val text = if (it.size < 100) it.size.toString() else "99+"

    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .size(28.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
internal fun StationIcon(type: BusStopMapItem.Type, isSelected: Boolean) {
    val busIcon = remember { Icons.Outlined.DirectionsBus }
    val tramIcon = remember { Icons.Outlined.Tram }

    Icon(
        imageVector = if (type == BusStopMapItem.Type.Tram) tramIcon else busIcon,
        contentDescription = "Przystanek",
        tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
    )
}