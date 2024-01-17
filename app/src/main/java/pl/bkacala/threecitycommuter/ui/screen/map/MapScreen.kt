package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

@Composable
fun MapScreen() {
    val viewModel = hiltViewModel<MapScreenViewModel>()

    val busStops = viewModel.busStop.collectAsState().value

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize()) {
            busStops.fastForEach {
                Marker(
                    state = MarkerState(position = LatLng(it.stopLat, it.stopLon)),
                )
            }
        }
    }
}