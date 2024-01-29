package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.component.map.BusStopClusterItem
import pl.bkacala.threecitycommuter.utils.changeColor

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun Map(
    cameraPositionState: CameraPositionState,
    viewModel: MapScreenViewModel,
) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val iconBitmap = remember { ContextCompat.getDrawable(context, R.drawable.bus_station)?.toBitmap()?.changeColor(color) }

    LaunchedEffect(viewModel) {
        viewModel.location.collect {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.0f)
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties().copy(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                context,
                mapStyle()
            )
        )
    ) {

        val busIcon = remember {
            BitmapDescriptorFactory.fromBitmap(iconBitmap!!)
        }

        val clusterManager = rememberClusterManager<BusStopClusterItem>()
        val clusterRenderer = rememberBusStopsClusterRenderer(
            clusterManager = clusterManager,
            itemBitmap = busIcon
        )
        clusterManager?.setOnClusterItemClickListener {

            false
        }

        if (clusterManager != null && clusterRenderer!= null) {
            clusterManager.renderer = clusterRenderer

            when (val busStops = viewModel.busStops.collectAsState().value) {
                is UiState.Error -> {

                }
                UiState.Loading -> {
                }
                is UiState.Success -> {


                    Clustering(
                        items = busStops.data,
                        clusterManager = clusterManager
                    )
                }
            }
        }
    }
}


@Composable
private fun mapStyle(): Int {
    return if (isSystemInDarkTheme()) {
        R.raw.google_dark_mode_map_style
    } else {
        R.raw.google_light_mode_map_style
    }
}