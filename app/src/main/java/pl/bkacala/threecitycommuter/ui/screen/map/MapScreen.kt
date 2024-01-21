package pl.bkacala.threecitycommuter.ui.screen.map

import android.content.res.Resources.Theme
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.clustering.Clustering
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.ui.common.UiState

@Composable
fun MapScreen() {
    val viewModel = hiltViewModel<MapScreenViewModel>()
    val context = LocalContext.current

    when(val busStops = viewModel.busStops.collectAsState().value) {
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
                    properties = MapProperties().copy(
                        mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, mapStyle())
                    )
                ) {
                    Clustering(
                        items = busStops.data,
                        onClusterClick = { false },
                        onClusterItemClick = { _ -> true },
                        clusterContent = {
                            Box(modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary)) {
                                Text(text = it.size.toString())
                            }
                        },
                        clusterItemContent = {
                            Box(modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary)) {
                                Text(text = it.title ?: "")
                            }
                        }
                    )
                }
            }
        }
    }

}


@Composable
private fun mapStyle() : Int {
    return if(isSystemInDarkTheme()) {
        R.raw.google_dark_mode_map_style
    } else {
        R.raw.google_light_mode_map_style
    }
}