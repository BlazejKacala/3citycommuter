package pl.bkacala.threecitycommuter.ui.screen.map

import android.util.Log
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
import pl.bkacala.threecitycommuter.ui.common.UiState

@Composable
fun MapScreen() {
    val viewModel = hiltViewModel<MapScreenViewModel>()

    when(val busStops = viewModel.busStops.collectAsState().value) {
        is UiState.Error -> {
            Log.d("2137", busStops.exception.toString())
        }
        UiState.Loading -> {
            //TODO
        }
        is UiState.Success -> {
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(modifier = Modifier.fillMaxSize()) {
                    busStops.data.fastForEach {
                        Marker(
                            state = MarkerState(position = LatLng(it.stopLat, it.stopLon)),
                        )
                    }
                }
            }
        }
    }

}