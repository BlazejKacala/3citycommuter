package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheet

@OptIn(FlowPreview::class)
@Composable
fun MapScreen() {
    BoxWithConstraints {

        val viewModel = hiltViewModel<MapScreenViewModel>()
        val cameraPositionState = rememberCameraPositionState()

        LaunchedEffect(cameraPositionState) {
            snapshotFlow { cameraPositionState.position.target }
                .debounce(200)
                .distinctUntilChanged()
                .collect {
                    cameraPositionState.projection?.visibleRegion?.let {
                        viewModel.onMapMoved(it.latLngBounds)
                    }
                }
        }

        LaunchedEffect(viewModel) {
            viewModel.location.collect {
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.0f)
                )
            }
        }

        val busStops = viewModel.busStops.collectAsState().value
        val busStopsState = remember(viewModel) { mutableStateOf(emptyList<BusStopMapItem>()) }
        when(busStops) {
            is UiState.Error -> {
                //TODO
            }
            UiState.Loading -> {
                //TODO
            }
            is UiState.Success -> {
                busStopsState.value = busStops.data
            }
        }

        Map(
            cameraPositionState = cameraPositionState,
            busStops = busStopsState.value,
            onBusStationSelected = { viewModel.onBusStopSelected(it) }
        )
        DeparturesBottomSheet(departures = viewModel.departures.collectAsState().value)
    }

}