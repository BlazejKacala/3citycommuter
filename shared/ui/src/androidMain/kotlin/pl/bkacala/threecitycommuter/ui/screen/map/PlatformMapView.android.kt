package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlinx.coroutines.launch
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.model.MapStyle

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
    val surface = MaterialTheme.colorScheme.surface
    val stopsByIndex = remember(busStops) { busStops }
    val coroutineScope = rememberCoroutineScope()

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
            onMapClick = { _, _ ->
                onMapClicked()
                ClickResult.Pass
            },
        ) {
            val stopsSource = rememberGeoJsonSource(
                data = GeoJsonData.JsonString(busStops.toStopsGeoJson()),
                options = GeoJsonOptions(cluster = true, clusterRadius = 50, clusterMaxZoom = 15),
            )
            val routeSource = route?.takeIf { it.size >= 2 }?.let {
                rememberGeoJsonSource(GeoJsonData.JsonString(it.toRouteGeoJson()))
            }
            val vehicleSource = trackedVehicle?.let {
                rememberGeoJsonSource(GeoJsonData.JsonString(it.toVehicleGeoJson()))
            }
            val locationSource = userLocation?.takeIf { !it.isFixed }?.let {
                rememberGeoJsonSource(GeoJsonData.JsonString(it.toLocationGeoJson()))
            }
            val busPainter = rememberVectorPainter(Icons.Outlined.DirectionsBus)
            val tramPainter = rememberVectorPainter(Icons.Outlined.Tram)

            DrawRoute(routeSource, tertiary)
            DrawBusStops(stopsSource, stopsByIndex, primary, surface, busPainter, tramPainter, cameraState, coroutineScope, onBusStopSelected)
            DrawSelectedBusStop(selectedBusStop, tertiary, surface, busPainter)
            DrawTrackedVehicle(vehicleSource, tertiary, surface)
            DrawUserLocation(locationSource, tertiary)
        }
    }
}

@Composable
@MaplibreComposable
private fun DrawRoute(source: GeoJsonSource?, color: Color) {
    source?.let { LineLayer("route-line", it, color = const(color), width = const(4.dp)) }
}

@Composable
@MaplibreComposable
private fun DrawBusStops(
    source: GeoJsonSource,
    stops: List<BusStopMapItem>,
    primary: Color,
    surface: Color,
    busPainter: androidx.compose.ui.graphics.painter.Painter,
    tramPainter: androidx.compose.ui.graphics.painter.Painter,
    cameraState: org.maplibre.compose.camera.CameraState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onBusStopSelected: (BusStopMapItem) -> Unit,
) {
    CircleLayer(
        id = "stop-clusters", source = source, filter = Feature.has("cluster"), color = const(surface),
        radius = const(15.dp), strokeColor = const(primary), strokeWidth = const(2.dp),
        onClick = { features ->
            (features.firstOrNull()?.geometry as? Point)?.let { point ->
                coroutineScope.launch {
                    cameraState.animateTo(
                        CameraPosition(target = point.coordinates, zoom = cameraState.position.zoom + 1.0),
                    )
                }
            }
            ClickResult.Consume
        },
    )
    SymbolLayer("stop-cluster-count", source, "", filter = Feature.has("cluster"), textField = format(span(Feature["point_count_abbreviated"].convertToString())), textColor = const(primary), textSize = const(13.sp), textAllowOverlap = const(true))
    CircleLayer(
        id = "bus-stops", source = source, filter = !Feature.has("cluster"), color = const(surface),
        radius = const(11.dp), strokeColor = const(primary), strokeWidth = const(2.dp),
        onClick = { features ->
            features.firstOrNull()?.properties?.get("index")?.toString()?.toIntOrNull()?.let { index -> stops.getOrNull(index)?.let(onBusStopSelected) }
            ClickResult.Consume
        },
    )
    SymbolLayer("bus-stop-icon", source, "", filter = !Feature.has("cluster"), iconImage = image(busPainter, drawAsSdf = true), iconColor = const(primary), iconAllowOverlap = const(true))
    SymbolLayer("tram-stop-icon", source, "", filter = all(!Feature.has("cluster"), Feature["type"].asString() eq const("T")), iconImage = image(tramPainter, drawAsSdf = true), iconColor = const(primary), iconAllowOverlap = const(true))
}

@Composable
@MaplibreComposable
private fun DrawSelectedBusStop(stop: BusStopMapItem?, color: Color, surface: Color, painter: androidx.compose.ui.graphics.painter.Painter) {
    stop?.let {
        val source = rememberGeoJsonSource(GeoJsonData.JsonString(it.toSelectedStopGeoJson()))
        CircleLayer("selected-bus-stop", source, color = const(surface), radius = const(14.dp), strokeColor = const(color), strokeWidth = const(3.dp))
        SymbolLayer("selected-bus-stop-icon", source, "", iconImage = image(painter, drawAsSdf = true), iconColor = const(color), iconAllowOverlap = const(true))
    }
}

@Composable
@MaplibreComposable
private fun DrawTrackedVehicle(source: GeoJsonSource?, color: Color, surface: Color) {
    source?.let {
        CircleLayer("tracked-vehicle", it, color = const(surface), radius = const(17.dp), strokeColor = const(color), strokeWidth = const(2.dp))
        SymbolLayer("tracked-vehicle-number", it, "", textField = format(span(Feature["number"].asString())), textColor = const(color), textSize = const(11.sp), textAllowOverlap = const(true))
    }
}

@Composable
@MaplibreComposable
private fun DrawUserLocation(source: GeoJsonSource?, color: Color) {
    source?.let { CircleLayer("user-location-outer", it, color = const(color), radius = const(9.dp), strokeColor = const(Color.White), strokeWidth = const(2.dp)) }
}

private fun List<BusStopMapItem>.toStopsGeoJson() = buildString {
    append("{\"type\":\"FeatureCollection\",\"features\":[")
    this@toStopsGeoJson.forEachIndexed { index, stop ->
        if (index > 0) append(',')
        append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${stop.position.longitude},${stop.position.latitude}]},\"properties\":{\"index\":$index,\"type\":\"${stop.markerLabel()}\"}}")
    }
    append("]}")
}

private fun List<LatLng>.toRouteGeoJson() = buildString {
    append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
    this@toRouteGeoJson.forEachIndexed { index, point ->
        if (index > 0) append(',')
        append("[${point.longitude},${point.latitude}]")
    }
    append("]},\"properties\":{}}")
}

private fun BusStopMapItem.toSelectedStopGeoJson() = this.toPointGeoJson("\"type\":\"${markerLabel()}\"")

private fun TrackedVehicle.toVehicleGeoJson() =
    "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${position.longitude},${position.latitude}]},\"properties\":{\"number\":\"${number.jsonEscaped()}\"}}"

private fun UserLocation.toLocationGeoJson() =
    "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${longitude},${latitude}]},\"properties\":{}}"

private fun BusStopMapItem.toPointGeoJson(properties: String) =
    "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${position.longitude},${position.latitude}]},\"properties\":{$properties}}"

private fun BusStopMapItem.markerLabel() = when (getStationType()) {
    BusStopMapItem.Type.Bus -> "B"
    BusStopMapItem.Type.Tram -> "T"
    BusStopMapItem.Type.Both -> "B/T"
}

private fun String.jsonEscaped() = replace("\\", "\\\\").replace("\"", "\\\"")
