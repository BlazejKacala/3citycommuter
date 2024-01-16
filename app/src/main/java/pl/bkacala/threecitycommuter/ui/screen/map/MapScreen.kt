package pl.bkacala.threecitycommuter.ui.screen.map

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.maps.android.compose.GoogleMap

@Composable
fun MapScreen() {

    val viewModel = hiltViewModel<MapScreenViewModel>()
    val busStops = viewModel.busStop.collectAsState().value

    Log.d("2137", busStops.toString())

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize()) {

        }
    }
}