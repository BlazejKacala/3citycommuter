package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle

@Composable
actual fun PlatformMapView(
    modifier: Modifier,
    cameraTarget: LatLng?,
    cameraZoom: Float,
    busStops: List<BusStopMapItem>,
    selectedBusStop: BusStopMapItem?,
    trackedVehicle: TrackedVehicle?,
    route: List<LatLng>?,
    userLocation: UserLocation?,
    onBusStopSelected: (BusStopMapItem) -> Unit,
    onMapClicked: () -> Unit,
    mapBottomPadding: Dp,
) {
    // TODO: Implement iOS MapView using UIKitView interop with Mapbox iOS SDK
    // For now, placeholder view
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8))
            .pointerInput(Unit) {
                detectTapGestures {
                    onMapClicked()
                }
            }
    ) {
        Text(
            text = "iOS Map — ${busStops.size} przystanków",
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
