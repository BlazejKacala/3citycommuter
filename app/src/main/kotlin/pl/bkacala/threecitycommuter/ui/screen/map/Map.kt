package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun Map(
    cameraPositionState: CameraPositionState,
    busStops: List<BusStopMapItem>,
    onBusStationSelected: (busStation: BusStopMapItem) -> Unit,
    onMapClicked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties().copy(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                context,
                mapStyle()
            )
        ),
        onMapClick = {
            onMapClicked()
        }
    ) {
        Clustering(
            items = busStops,
            clusterItemContent = {
                BusStationIcon()
            },
            onClusterItemClick = {
                onBusStationSelected(it)
                true
            },
            clusterContent = {
                Cluster(it)
            },
            onClusterClick = {
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.zoomIn()
                    )
                }
                true
            },
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
        BusStationIcon()
    }
}

@Composable
private fun BusStationIcon() {
    Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.bus_station),
        contentDescription = "Przystanek",
        tint = MaterialTheme.colorScheme.primary
    )
}


@Composable
private fun mapStyle(): Int {
    return if (isSystemInDarkTheme()) {
        R.raw.google_dark_mode_map_style
    } else {
        R.raw.google_light_mode_map_style
    }
}