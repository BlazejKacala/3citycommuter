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
import androidx.compose.runtime.MutableState
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
import org.koin.compose.viewmodel.koinViewModel
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.TransitStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheet
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesHeaderModel
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
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        val mapBottomPadding = remember(viewModel) { mutableStateOf(0.dp) }
        val cameraTarget = remember { mutableStateOf<LatLng?>(null) }

        TraceLifecycleEvents(viewModel)
        HandleEffects(viewModel, snackbarHostState, cameraTarget)

        when (val transitStops = state.transitStops) {
            is UiState.Error -> ErrorSnackbar(transitStops, snackbarHostState, viewModel)
            UiState.Loading -> Unit
            is UiState.Success -> Unit
        }

        val transitStops = if (state.transitStops is UiState.Success) state.transitStops.data else emptyList()

        PlatformMapView(
            modifier = Modifier.fillMaxSize(),
            cameraTarget = cameraTarget.value,
            cameraZoom = 6.0f,
            transitStops = transitStops,
            selectedTransitStop = state.selectedTransitStop,
            trackedVehicle = state.trackedVehicle,
            userLocation = state.userLocation,
            onTransitStopSelected = { viewModel.onAction(MapAction.StopSelected(it.key)) },
            onMapClicked = { viewModel.onAction(MapAction.MapClicked) },
            route = state.route,
            mapBottomPadding = if (state.departures == null) 0.dp else mapBottomPadding.value,
            mapStyle = mapStyle,
            stadiaToken = stadiaToken,
        )

        if (state.showCenterOnPositionButton) {
            CenterOnLocationButton(
                onClicked = { viewModel.onAction(MapAction.CenterOnUserClicked) },
            )
        }

        BusSearchBar(
            query = state.searchQuery,
            isActive = state.isSearchActive,
            results = state.searchResults,
            onQueryChange = { viewModel.onAction(MapAction.SearchQueryChanged(it)) },
            onExpandedChange = { viewModel.onAction(MapAction.SearchActiveChanged(it)) },
            onResultClick = { viewModel.onAction(MapAction.SearchResultClicked(it)) },
        )

        if (state.transitStops is UiState.Loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
        }

        DeparturesSheet(
            departuresState = state.departures,
            selectedStop = state.selectedTransitStop,
            viewModel = viewModel,
        ) {
            mapBottomPadding.value = it
        }
    }
}

@Composable
private fun ErrorSnackbar(
    transitStops: UiState<List<TransitStopMapItem>>,
    snackbarHostState: SnackbarHostState,
    viewModel: MapScreenViewModel,
) {
    LaunchedEffect(transitStops) {
        val result = snackbarHostState.showSnackbar(
            message = STOPS_LOADING_ERROR_MESSAGE,
            actionLabel = RETRY_ACTION_LABEL,
        )
        when (result) {
            SnackbarResult.Dismissed -> {}
            SnackbarResult.ActionPerformed -> {
                viewModel.onAction(MapAction.ReloadClicked)
            }
        }
    }
}

@Composable
private fun HandleEffects(
    viewModel: MapScreenViewModel,
    snackbarHostState: SnackbarHostState,
    cameraTarget: MutableState<LatLng?>,
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MapEffect.FocusCamera -> cameraTarget.value = effect.target
                is MapEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
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
                Lifecycle.Event.ON_RESUME -> viewModel.onAction(MapAction.ScreenResumed)
                Lifecycle.Event.ON_PAUSE -> viewModel.onAction(MapAction.ScreenPaused)
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
    departuresState: UiState<DeparturesBottomSheetModel>?,
    selectedStop: TransitStopMapItem?,
    viewModel: MapScreenViewModel,
    maxSizeListener: (maxSize: Dp) -> Unit,
) {
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = departuresState != null && selectedStop != null,
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
        val isLoading = departuresState is UiState.Loading || departuresState is UiState.Error
        val model = (departuresState as? UiState.Success)?.data ?: selectedStop?.let {
            DeparturesBottomSheetModel(
                provider = it.data.provider,
                header = DeparturesHeaderModel(
                    transitStopName = it.data.name,
                    isForDemand = it.data.onDemand,
                ),
                departures = emptyList(),
            )
        }
        model?.let {
            DeparturesBottomSheet(
                model = it,
                isLoading = isLoading,
                animationKey = selectedStop?.key?.toString() ?: it.header.transitStopName,
                onDepartureSelected = { viewModel.onAction(MapAction.DepartureSelected(it)) },
            )
        }
    }
}

private const val STOPS_LOADING_ERROR_MESSAGE = "Nie udało się wczytać przystanków"
private const val RETRY_ACTION_LABEL = "Spróbuj ponownie"
