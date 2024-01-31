package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheet

@OptIn(FlowPreview::class)
@Composable
fun MapScreen() {

    BoxWithConstraints {

        val viewModel = hiltViewModel<MapScreenViewModel>()
        val cameraPositionState = rememberCameraPositionState()


        LaunchedEffect(cameraPositionState) {
            snapshotFlow { cameraPositionState.position.target }
                .debounce(100)
                .distinctUntilChanged()
                .collect {
                    cameraPositionState.projection?.visibleRegion?.let {
                        viewModel.onMapMoved(it.latLngBounds)
                    }
                }
        }

        Map(
            cameraPositionState = cameraPositionState,
            viewModel = viewModel,
            onBusStationSelected = { viewModel.onBusStopSelected(it) }
        )
        DeparturesBottomSheet(departures = viewModel.departures.collectAsState().value)


    }

}