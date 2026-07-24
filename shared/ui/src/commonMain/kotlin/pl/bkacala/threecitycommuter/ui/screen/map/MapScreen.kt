package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.viewmodel.koinViewModel
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheet
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.model.MapStyle
import pl.bkacala.threecitycommuter.ui.screen.map.search.BusSearchBar

@Composable
fun MapScreen(
    snackbarHostState: SnackbarHostState,
    mapStyle: MapStyle = MapStyle.OSM_RASTER,
    stadiaToken: String? = null,
) {
    BoxWithConstraints {
        val viewModel = koinViewModel<MapScreenViewModel>()

        TraceLifecycleEvents(viewModel)
        HandleErrorFlow(viewModel, snackbarHostState)

        val busStopsState = remember(viewModel) { mutableStateOf(emptyList<BusStopMapItem>()) }
        val displayLoader = remember(viewModel) { mutableStateOf(false) }

        val busStops = viewModel.busStops.collectAsStateWithLifecycle().value
        when (busStops) {
            is UiState.Error -> {
                displayLoader.value = false
                ErrorSnackbar(busStops, snackbarHostState, viewModel)
            }
            UiState.Loading -> {
                displayLoader.value = true
            }
            is UiState.Success -> {
                displayLoader.value = false
                busStopsState.value = busStops.data
            }
        }

        val userLocation = viewModel.location.collectAsStateWithLifecycle().value
        val departuresModel = viewModel.departures.collectAsStateWithLifecycle().value
        val mapBottomPadding = remember(viewModel) { mutableStateOf(0.dp) }

        val cameraTarget = remember { mutableStateOf<pl.bkacala.threecitycommuter.model.LatLng?>(null) }
        LaunchedEffect(viewModel) {
            viewModel.cameraPosition.filterNotNull().collectLatest {
                cameraTarget.value = it
            }
        }

        PlatformMapView(
            modifier = Modifier.fillMaxSize(),
            cameraTarget = cameraTarget.value,
            cameraZoom = 6.0f,
            busStops = busStopsState.value,
            selectedBusStop = viewModel.selectedBusStop.collectAsStateWithLifecycle().value,
            trackedVehicle = viewModel.trackedVehicle.collectAsStateWithLifecycle().value,
            userLocation = userLocation,
            onBusStopSelected = { viewModel.onBusStopSelected(it) },
            onMapClicked = { viewModel.onMapClicked() },
            route = viewModel.route.collectAsStateWithLifecycle().value,
            mapBottomPadding = if (departuresModel == null) 0.dp else mapBottomPadding.value,
            mapStyle = mapStyle,
            stadiaToken = stadiaToken,
        )

        val displayCenterOnLocationButton =
            viewModel.centerOnPositionVisibility.collectAsStateWithLifecycle().value
        if (displayCenterOnLocationButton) {
            CenterOnLocationButton(
                onClicked = { viewModel.centerOnUserPosition() },
            )
        }

        BusSearchBar(searchBarModel = viewModel.searchBarModel)

        if (busStops is UiState.Loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
        }
        DeparturesSheet(departuresModel) {
            mapBottomPadding.value = it
        }
    }
}

@Composable
private fun ErrorSnackbar(
    busStops: UiState<List<BusStopMapItem>>,
    snackbarHostState: SnackbarHostState,
    viewModel: MapScreenViewModel,
) {
    LaunchedEffect(busStops) {
        val result = snackbarHostState.showSnackbar(
            message = "Nie udało się wczytać przystanków",
            actionLabel = "Spróbuj ponownie",
        )
        when (result) {
            SnackbarResult.Dismissed -> {}
            SnackbarResult.ActionPerformed -> {
                viewModel.onMapReloadRequest()
            }
        }
    }
}

@Composable
fun HandleErrorFlow(viewModel: MapScreenViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(viewModel) {
        viewModel.errorFlow.collectLatest {
            it.printStackTrace()
            snackbarHostState.showSnackbar("Nie udało się wczytać danych")
        }
    }
}

@Composable
fun BoxWithConstraintsScope.CenterOnLocationButton(onClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .absoluteOffset(x = maxWidth.minus(73.dp), y = 120.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .size(58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClicked() },
    ) {
        Icon(
            imageVector = Icons.Default.GpsFixed,
            contentDescription = "Wycentruj na twojej pozycji",
            modifier = Modifier.align(Alignment.Center),
        )
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
private fun BoxWithConstraintsScope.DeparturesSheet(
    departuresModel: DeparturesBottomSheetModel?,
    maxSizeListener: (maxSize: Dp) -> Unit,
) {
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = departuresModel != null,
        enter = slideInVertically { fullHeight ->
            with(density) {
                maxSizeListener(fullHeight.toDp())
            }
            fullHeight
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .heightIn(min = 0.dp, max = maxHeight * 2 / 5),
    ) {
        departuresModel?.let {
            DeparturesBottomSheet(
                model = departuresModel,
            )
        }
    }
}
