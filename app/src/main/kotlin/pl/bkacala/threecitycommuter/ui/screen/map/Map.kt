package pl.bkacala.threecitycommuter.ui.screen.map

import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Man
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.R
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem

@Composable
fun Map(
    cameraPositionState: CameraPositionState,
    busStops: List<BusStopMapItem>,
    selectedBusStop: BusStopMapItem?,
    onBusStationSelected: (busStation: BusStopMapItem) -> Unit,
    onMapClicked: () -> Unit,
    userLocation: UserLocation?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                context,
                mapStyle()
            ),
            minZoomPreference = 10.0f,
            latLngBoundsForCameraTarget = LatLngBounds(
                LatLng(54.2783173, 18.5874054), //south-west
                LatLng(54.5413784, 18.792546)) //north-east
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
                        CameraUpdateFactory.zoomIn()
                    )
                }
            }
        )
        selectedBusStop?.let {
            SelectedBusStop(it)
        }

    }
}

@Composable
private fun SelectedBusStop(busStopMapItem: BusStopMapItem) {
    Log.d("2137", busStopMapItem.position.toString())
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
                userLocation,
                state = rememberMarkerState(
                    key = userLocation.toString(),
                    position = LatLng(userLocation.latitude, userLocation.longitude)
                )
            ) {
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