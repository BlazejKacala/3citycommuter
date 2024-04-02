package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.component.VehicleType

@Composable
fun Map(
    cameraPositionState: CameraPositionState,
    busStops: List<BusStopMapItem>,
    selectedBusStop: BusStopMapItem?,
    trackedVehicle: TrackedVehicle?,
    onBusStationSelected: (busStation: BusStopMapItem) -> Unit,
    onMapClicked: () -> Unit,
    userLocation: UserLocation?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        contentPadding = PaddingValues(top = 100.dp),
        properties = MapProperties(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                context,
                mapStyle()
            ),
            minZoomPreference = 10.0f,
            latLngBoundsForCameraTarget = LatLngBounds(
                LatLng(54.0783173, 18.1874054), //south-west
                LatLng(54.9413784, 18.922546) //north-east
            )
        ),
        onMapClick = {
            onMapClicked()
        }
    ) {
        UserLocationMarker(userLocation)
        BusStops(
            busStops = busStops,
            onClusterItemClick = { clickedBusStop ->
                onBusStationSelected(clickedBusStop)
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLng(clickedBusStop.position),
                        durationMs = 300
                    )
                }
            },
            onClusterClick = {
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            it.position,
                            cameraPositionState.position.zoom * 1.2f
                        )
                    )
                }
            }
        )
        selectedBusStop?.let {
            SelectedBusStop(it)
        }
        trackedVehicle?.let {
            TrackedVehicleMarker(it)
        }
    }
}

@Composable
fun TrackedVehicleMarker(trackedVehicle: TrackedVehicle) {

    val lat = animateFloatAsState(
        targetValue = trackedVehicle.position.latitude.toFloat(),
        label = "lat",
        visibilityThreshold = 0.001f,
        animationSpec = tween(2000)
    )
    val long = animateFloatAsState(
        targetValue = trackedVehicle.position.longitude.toFloat(),
        label = "long",
        visibilityThreshold = 0.001f,
        animationSpec = tween(2000)
    )

    MarkerComposable(
        trackedVehicle, lat.value, long.value,
        state = MarkerState(LatLng(lat.value.toDouble(), long.value.toDouble())),
        zIndex = 10f
    ) {
        MapMarkerBackground(color = MaterialTheme.colorScheme.tertiary) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val busIcon = remember { Icons.Filled.DirectionsBus }
                val tramIcon = remember { Icons.Filled.Tram }
                Text(
                    text = trackedVehicle.number,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (trackedVehicle.type == VehicleType.Tram) tramIcon else busIcon,
                    contentDescription = "Pojazd",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun SelectedBusStop(busStopMapItem: BusStopMapItem) {
    MarkerComposable(
        busStopMapItem,
        state = MarkerState(busStopMapItem.position),
        zIndex = 10f
    ) {
        StationIcon(type = busStopMapItem.getStationType(), isSelected = true)
    }
}

@Composable
private fun UserLocationMarker(userLocation: UserLocation?) {
    userLocation?.let {
        if (!userLocation.isFixed) {
            MarkerComposable(
                state = MarkerState(
                    LatLng(
                        userLocation.latitude,
                        userLocation.longitude
                    )
                )
            )
            {
                Icon(
                    imageVector = Icons.Filled.Man,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                    contentDescription = "Pozycja użytkownika"
                )
            }
        }
    }
}


@Composable
private fun mapStyle(): Int {
    return if (isSystemInDarkTheme()) {
        R.raw.google_dark_mode_map_style
    } else {
        R.raw.google_light_mode_map_style
    }
}