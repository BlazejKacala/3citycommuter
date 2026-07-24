package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.sphericalDistance
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow

internal data class StopMapMarker(
    val position: LatLng,
    val stop: BusStopMapItem?,
    val count: Int,
)

internal fun List<BusStopMapItem>.toStopMapMarkers(zoom: Int): List<StopMapMarker> {
    val stopsWithIndex = withIndex().toList()
    val groups = if (zoom >= STOPS_UNCLUSTERED_ZOOM) {
        stopsWithIndex.map { listOf(it) }
    } else {
        val longitudeCellSize = 360.0 / 2.0.pow(zoom) * CLUSTER_RADIUS_TILE_FRACTION
        val latitudeCellSize = longitudeCellSize * cos(MAP_REFERENCE_LATITUDE * PI / 180.0)
        stopsWithIndex.groupBy { indexedStop ->
            Pair(
                floor(indexedStop.value.position.longitude / longitudeCellSize).toLong(),
                floor(indexedStop.value.position.latitude / latitudeCellSize).toLong(),
            )
        }.values
    }

    return groups.map { group ->
        if (group.size == 1) {
            val stop = group.first().value
            StopMapMarker(position = stop.position, stop = stop, count = 1)
        } else {
            StopMapMarker(
                position = LatLng(
                    latitude = group.sumOf { it.value.position.latitude } / group.size,
                    longitude = group.sumOf { it.value.position.longitude } / group.size,
                ),
                stop = null,
                count = group.size,
            )
        }
    }
}

internal fun findStopMarkerAt(
    markers: List<StopMapMarker>,
    click: Position,
    metersPerDp: Double,
): StopMapMarker? {
    val clickPosition = LatLng(
        latitude = click.latitude,
        longitude = click.longitude,
    )
    val clickRadiusMeters = (metersPerDp * MARKER_CLICK_RADIUS_DP).coerceAtLeast(
        MIN_MARKER_CLICK_RADIUS_METERS,
    )

    return markers
        .map { marker ->
            marker to marker.position.sphericalDistance(clickPosition)
        }
        .filter { (_, distance) -> distance <= clickRadiusMeters }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

@Composable
internal fun MapMarkersOverlay(
    modifier: Modifier,
    cameraState: CameraState,
    stopMarkers: List<StopMapMarker>,
    selectedBusStop: BusStopMapItem?,
    trackedVehicle: TrackedVehicle?,
    stopColor: Color,
    selectedStopColor: Color,
    vehicleColor: Color,
) {
    val busPainter = rememberVectorPainter(Icons.Outlined.DirectionsBus)
    val tramPainter = rememberVectorPainter(Icons.Outlined.Tram)
    val textMeasurer = rememberTextMeasurer()
    val countTextStyle = remember {
        TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    val vehicleTextStyle = remember {
        TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    Canvas(modifier.clipToBounds()) {
        // The projection changes inside the native map without changing its object identity.
        // Reading the camera state here keeps every marker element in sync with the same camera move.
        cameraState.position
        val projection = cameraState.projection ?: return@Canvas

        stopMarkers.forEach { marker ->
            if (marker.stop?.id == selectedBusStop?.id) return@forEach

            val location = projection.screenLocationFromPosition(marker.position.toPosition())
            val center = Offset(location.x.toPx(), location.y.toPx())
            if (!center.isInside(size)) return@forEach

            val isCluster = marker.stop == null
            drawCircle(
                color = Color.White,
                radius = (if (isCluster) CLUSTER_OUTER_RADIUS else STOP_OUTER_RADIUS).toPx(),
                center = center,
            )
            drawCircle(
                color = stopColor,
                radius = (if (isCluster) CLUSTER_INNER_RADIUS else STOP_INNER_RADIUS).toPx(),
                center = center,
            )

            if (isCluster) {
                val count = if (marker.count < 100) marker.count.toString() else "99+"
                val text = textMeasurer.measure(count, countTextStyle)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(
                        center.x - text.size.width / 2f,
                        center.y - text.size.height / 2f,
                    ),
                )
            } else {
                val painter = when (marker.stop.getStationType()) {
                    BusStopMapItem.Type.Tram -> tramPainter
                    else -> busPainter
                }
                val iconSize = STOP_ICON_SIZE.toPx()
                translate(
                    left = center.x - iconSize / 2f,
                    top = center.y - iconSize / 2f,
                ) {
                    with(painter) {
                        draw(
                            size = Size(iconSize, iconSize),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
        }

        selectedBusStop?.let { stop ->
            val location = projection.screenLocationFromPosition(stop.position.toPosition())
            val center = Offset(location.x.toPx(), location.y.toPx())
            if (center.isInside(size)) {
                drawCircle(Color.White, radius = SELECTED_STOP_OUTER_RADIUS.toPx(), center = center)
                drawCircle(
                    color = selectedStopColor,
                    radius = SELECTED_STOP_INNER_RADIUS.toPx(),
                    center = center,
                )
                val painter = when (stop.getStationType()) {
                    BusStopMapItem.Type.Tram -> tramPainter
                    else -> busPainter
                }
                val iconSize = SELECTED_STOP_ICON_SIZE.toPx()
                translate(
                    left = center.x - iconSize / 2f,
                    top = center.y - iconSize / 2f,
                ) {
                    with(painter) {
                        draw(
                            size = Size(iconSize, iconSize),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
        }

        trackedVehicle?.let { vehicle ->
            val location = projection.screenLocationFromPosition(vehicle.position.toPosition())
            val center = Offset(location.x.toPx(), location.y.toPx())
            if (center.isInside(size)) {
                drawCircle(Color.White, radius = 22.dp.toPx(), center = center)
                drawCircle(vehicleColor, radius = 18.dp.toPx(), center = center)
                val text = textMeasurer.measure(vehicle.number, vehicleTextStyle)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(
                        center.x - text.size.width / 2f,
                        center.y - text.size.height / 2f,
                    ),
                )
            }
        }
    }
}

private fun LatLng.toPosition() = Position(longitude, latitude)

private fun Offset.isInside(canvasSize: Size): Boolean =
    x >= -MARKER_EDGE_MARGIN_PX &&
        y >= -MARKER_EDGE_MARGIN_PX &&
        x <= canvasSize.width + MARKER_EDGE_MARGIN_PX &&
        y <= canvasSize.height + MARKER_EDGE_MARGIN_PX

private const val STOPS_UNCLUSTERED_ZOOM = 16
private const val CLUSTER_RADIUS_TILE_FRACTION = 50.0 / 512.0
private const val MAP_REFERENCE_LATITUDE = 54.352
private val CLUSTER_OUTER_RADIUS = 22.dp
private val CLUSTER_INNER_RADIUS = 17.dp
private val STOP_OUTER_RADIUS = 18.dp
private val STOP_INNER_RADIUS = 13.dp
private val STOP_ICON_SIZE = 20.dp
private val SELECTED_STOP_OUTER_RADIUS = 24.dp
private val SELECTED_STOP_INNER_RADIUS = 19.dp
private val SELECTED_STOP_ICON_SIZE = STOP_ICON_SIZE
private const val MARKER_CLICK_RADIUS_DP = 56f
private const val MIN_MARKER_CLICK_RADIUS_METERS = 15.0
private const val MARKER_EDGE_MARGIN_PX = 64f
