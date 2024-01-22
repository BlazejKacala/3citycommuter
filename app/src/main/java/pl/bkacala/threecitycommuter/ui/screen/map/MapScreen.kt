package pl.bkacala.threecitycommuter.ui.screen.map

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.clustering.algo.NonHierarchicalViewBasedAlgorithm
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.clustering.rememberClusterRenderer
import com.google.maps.android.compose.rememberCameraPositionState
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.component.map.BusStopClusterItem

@Composable
fun MapScreen() {

    BoxWithConstraints {

        val viewModel = hiltViewModel<MapScreenViewModel>()
        val context = LocalContext.current
        val cameraPositionState = rememberCameraPositionState()

        when (val busStops = viewModel.busStops.collectAsState().value) {

            is UiState.Error -> {
                Log.d("2137", busStops.exception.toString())
            }

            UiState.Loading -> {
                //TODO
            }

            is UiState.Success -> {

                Box(modifier = Modifier.fillMaxSize()) {
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
                        val clusterManager = rememberClusterManager<BusStopClusterItem>()
                        val clusterRenderer = rememberBusStopsClusterRenderer(clusterManager = clusterManager)
                        if (clusterManager != null && clusterRenderer!= null && clusterManager.renderer != clusterRenderer) {
                            clusterManager.renderer = clusterRenderer
                            Clustering(
                                items = busStops.data,
                                clusterManager = clusterManager
                            )
                        }
                    }
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