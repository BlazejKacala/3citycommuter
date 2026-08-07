package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.component.TransitStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.model.MapStyle

@Composable
expect fun PlatformMapView(
    modifier: Modifier,
    cameraTarget: LatLng?,
    cameraZoom: Float,
    transitStops: List<TransitStopMapItem>,
    selectedTransitStop: TransitStopMapItem?,
    trackedVehicle: TrackedVehicle?,
    route: List<LatLng>?,
    userLocation: UserLocation?,
    onTransitStopSelected: (TransitStopMapItem) -> Unit,
    onMapClicked: () -> Unit,
    mapBottomPadding: Dp,
    mapStyle: MapStyle,
    stadiaToken: String?,
)
