package pl.bkacala.threecitycommuter.ui.screen.map

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import pl.bkacala.threecitycommuter.LocalSnackbarHostState
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheet
import pl.bkacala.threecitycommuter.ui.screen.map.search.BusSearchBar

@Composable
fun MapScreen() {
    BoxWithConstraints {

        val viewModel = hiltViewModel<MapScreenViewModel>()
        val cameraPositionState = rememberCameraPositionState()
        val snackbarHostState = LocalSnackbarHostState.current

        TraceLifecycleEvents(viewModel)
        TraceMapCamera(viewModel, cameraPositionState)

        val busStops = viewModel.busStops.collectAsStateWithLifecycle().value
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

        val userLocation = viewModel.location.collectAsStateWithLifecycle().value
        Map(
            cameraPositionState = cameraPositionState,
            busStops = busStopsState.value,
            selectedBusStop = viewModel.selectedBusStop.collectAsStateWithLifecycle().value,
            trackedVehicle = viewModel.trackedVehicle.collectAsStateWithLifecycle().value,
            userLocation = userLocation,
            onBusStationSelected = { viewModel.onBusStopSelected(it) },
            onMapClicked = { viewModel.onMapClicked() },
        )

        val displayCenterOnLocationButton =
            viewModel.centerOnPositionVisibility.collectAsStateWithLifecycle().value
        if (displayCenterOnLocationButton) {
            CenterOnLocationButton(
                onClicked = { viewModel.centerOnUserPosition() }
            )
        }

        BusSearchBar(searchBarModel = viewModel.searchBarModel)

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
fun BoxWithConstraintsScope.CenterOnLocationButton(onClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .absoluteOffset(x = maxWidth.minus(73.dp), y = 120.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            )
            .size(58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClicked() }

    ) {
        Icon(
            imageVector = Icons.Default.GpsFixed,
            contentDescription = "Wycentruj na twojej pozycji",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun TraceMapCamera(
    viewModel: MapScreenViewModel,
    cameraPositionState: CameraPositionState
) {
    LaunchedEffect(viewModel) {
        viewModel.cameraPosition.filterNotNull().collectLatest {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.0f)
            )
        }
    }
}

@Composable
private fun TraceLifecycleEvents(viewModel: MapScreenViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.startTracingJobs()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopTracingJobs()
                }

                else -> {}
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.DeparturesSheet(model: MapScreenViewModel) {

    val departuresModel = model.departures.collectAsStateWithLifecycle().value

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