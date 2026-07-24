package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.model.MapStyle
import pl.bkacala.threecitycommuter.ui.theme.MapVehicleColor
import kotlin.math.roundToInt

private val defaultCenter = LatLng(54.3520, 18.6466)

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
    mapStyle: MapStyle,
    stadiaToken: String?,
) {
    val effectiveZoom = cameraZoom.coerceAtLeast(mapStyle.defaultZoom)
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                (cameraTarget ?: defaultCenter).longitude,
                (cameraTarget ?: defaultCenter).latitude,
            ),
            zoom = effectiveZoom.toDouble(),
        ),
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val coroutineScope = rememberCoroutineScope()
    val stopsZoom = cameraState.position.zoom.roundToInt()
    val stopMarkers = remember(busStops, stopsZoom) {
        busStops.toStopMapMarkers(stopsZoom)
    }
    val currentStopMarkers = rememberUpdatedState(stopMarkers)
    val currentOnBusStopSelected = rememberUpdatedState(onBusStopSelected)
    val currentOnMapClicked = rememberUpdatedState(onMapClicked)

    LaunchedEffect(cameraTarget, cameraZoom) {
        val target = cameraTarget ?: defaultCenter
        cameraState.animateTo(
            CameraPosition(
                target = Position(target.longitude, target.latitude),
                zoom = effectiveZoom.toDouble(),
            ),
        )
    }

    Box(modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            baseStyle = mapStyle.toBaseStyle(stadiaToken),
            onMapClick = { position, _ ->
                val marker = findStopMarkerAt(
                    markers = currentStopMarkers.value,
                    click = position,
                    metersPerDp = cameraState.metersPerDpAtTarget,
                )
                when {
                    marker?.stop != null -> {
                        currentOnBusStopSelected.value(marker.stop)
                        ClickResult.Consume
                    }
                    marker != null -> {
                        coroutineScope.launch {
                            cameraState.animateTo(
                                CameraPosition(
                                    target = Position(
                                        marker.position.longitude,
                                        marker.position.latitude,
                                    ),
                                    zoom = cameraState.position.zoom + 1.0,
                                ),
                            )
                        }
                        ClickResult.Consume
                    }
                    else -> {
                        currentOnMapClicked.value()
                        ClickResult.Pass
                    }
                }
            },
        ) {
            route?.takeIf { it.size >= 2 }?.let { routePoints ->
                key("route-source") {
                    val routeSource = rememberGeoJsonSource(
                        data = GeoJsonData.JsonString(routePoints.toRouteGeoJson()),
                        options = GeoJsonOptions(synchronousUpdate = true),
                    )
                    DrawRoute(routeSource, tertiary)
                }
            }
            userLocation?.takeIf { !it.isFixed }?.let { location ->
                key("user-location-source") {
                    val locationSource = rememberGeoJsonSource(
                        data = GeoJsonData.JsonString(location.toLocationGeoJson()),
                        options = GeoJsonOptions(synchronousUpdate = true),
                    )
                    DrawUserLocation(locationSource, primary)
                }
            }
        }
        MapMarkersOverlay(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            stopMarkers = stopMarkers,
            selectedBusStop = selectedBusStop,
            trackedVehicle = trackedVehicle,
            stopColor = primary,
            selectedStopColor = tertiary,
            vehicleColor = MapVehicleColor,
        )
    }
}

@Composable
@MaplibreComposable
private fun DrawRoute(source: GeoJsonSource, color: Color) {
    LineLayer("route-line-halo", source, color = const(Color.White), width = const(9.dp))
    LineLayer("route-line", source, color = const(color), width = const(5.dp))
}

@Composable
@MaplibreComposable
private fun DrawUserLocation(source: GeoJsonSource, color: Color) {
    CircleLayer("user-location-halo", source, color = const(Color.White), radius = const(12.dp))
    CircleLayer("user-location", source, color = const(color), radius = const(8.dp))
}

private fun List<LatLng>.toRouteGeoJson() = buildString {
    append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
    this@toRouteGeoJson.forEachIndexed { index, point ->
        if (index > 0) append(',')
        append("[${point.longitude},${point.latitude}]")
    }
    append("]},\"properties\":{}}")
}

private fun UserLocation.toLocationGeoJson() =
    "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$longitude,$latitude]},\"properties\":{}}"

private fun BusStopMapItem.toPointGeoJson(properties: String) =
    "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${position.longitude},${position.latitude}]},\"properties\":{$properties}}"
