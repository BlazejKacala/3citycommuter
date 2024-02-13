package pl.bkacala.threecitycommuter.ui.screen.map

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import pl.bkacala.threecitycommuter.LocalSnackbarHostState
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheet

@Composable
fun MapScreen() {
    BoxWithConstraints {

        val viewModel = hiltViewModel<MapScreenViewModel>()
        val cameraPositionState = rememberCameraPositionState()
        val snackbarHostState = LocalSnackbarHostState.current
        var userLocation by remember { mutableStateOf<UserLocation?>(null) }

        LaunchedEffect(viewModel) {
            viewModel.location.collect {
                userLocation = it
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 18.0f)
                )
            }
        }

        val busStops = viewModel.busStops.collectAsState().value
        val busStopsState = remember(viewModel) { mutableStateOf(emptyList<BusStopMapItem>()) }
        when (busStops) {
            is UiState.Error -> {
                Log.e("MapScreen", busStops.exception.stackTraceToString())
                LaunchedEffect(busStops) {
                    val result = snackbarHostState.showSnackbar(
                        message = "Nie udało się wczytać przystanków",
                        actionLabel = "Spróbuj ponownie"

                    )
                    when (result) {
                        SnackbarResult.Dismissed -> {}
                        SnackbarResult.ActionPerformed -> {
                            viewModel.onMapReloadRequest()
                        }
                    }

                }
            }

            UiState.Loading -> {}
            is UiState.Success -> {
                busStopsState.value = busStops.data
            }
        }

        Map(
            cameraPositionState = cameraPositionState,
            busStops = busStopsState.value,
            selectedBusStop = viewModel.selectedBusStop.collectAsState().value,
            userLocation = userLocation,
            onBusStationSelected = { viewModel.onBusStopSelected(it) },
            onMapClicked = { viewModel.onMapClicked() },
        )

        BusSearchBar()

        if (busStops is UiState.Loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }
        DeparturesSheet(model = viewModel)
    }

}

@Composable
private fun BoxWithConstraintsScope.DeparturesSheet(model: MapScreenViewModel) {

    val departuresModel = model.departures.collectAsState().value

    AnimatedVisibility(
        visible = departuresModel != null,
        enter = slideInVertically { fullHeight ->
            fullHeight
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .heightIn(min = 0.dp, max = maxHeight * 2 / 5)
    ) {
        departuresModel?.let {
            DeparturesBottomSheet(
                model = departuresModel,
            )
        }
    }

}