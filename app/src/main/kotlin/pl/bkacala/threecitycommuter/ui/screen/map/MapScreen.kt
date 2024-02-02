package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

@OptIn(FlowPreview::class, ExperimentalAnimationApi::class)
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
        when (busStops) {
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

        val departures = viewModel.departures.collectAsState().value
        AnimatedContent(
            targetState = departures,
            label = "Tablica odjazdów",
            transitionSpec = {
                (slideInVertically(
                    animationSpec = tween(400),
                    initialOffsetY = { fullHeight -> fullHeight/2 })
                ).togetherWith(
                    slideOutVertically (
                        animationSpec = tween(400),
                        targetOffsetY = { fullHeight -> fullHeight/2 }
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp, max = maxHeight / 2)
                .align(Alignment.BottomCenter)
        ) {
            DeparturesBottomSheet(
                departures = it,
            )
        }

    }

}