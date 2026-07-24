package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
    renderTick: State<Int>,
    stopMarkers: List<StopMapMarker>,
    trackedVehicle: TrackedVehicle?,
    stopColor: Color,
    vehicleColor: Color,
) {
    val busPainter = rememberVectorPainter(Icons.Outlined.DirectionsBus)
    val tramPainter = rememberVectorPainter(Icons.Outlined.Tram)
    val textMeasurer = rememberTextMeasurer()
    val countTextStyle = remember {
        TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    val vehicleTextStyle = remember {
        TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    Canvas(modifier.clipToBounds()) {
        // The projection changes inside the native map without changing its object identity.
        // Observing the frame tick in the draw phase invalidates only this canvas during movement.
        renderTick.value
        val projection = cameraState.projection ?: return@Canvas

        stopMarkers.forEach { marker ->
            val location = projection.screenLocationFromPosition(marker.position.toPosition())
            val center = Offset(location.x.toPx(), location.y.toPx())
            if (!center.isInside(size)) return@forEach

            val isCluster = marker.stop == null
            drawCircle(
                color = Color.White,
                radius = (if (isCluster) 18.dp else 13.dp).toPx(),
                center = center,
            )
            drawCircle(
                color = stopColor,
                radius = (if (isCluster) 14.dp else 9.dp).toPx(),
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
                val iconSize = 14.dp.toPx()
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
private const val MARKER_CLICK_RADIUS_DP = 48f
private const val MIN_MARKER_CLICK_RADIUS_METERS = 15.0
private const val MARKER_EDGE_MARGIN_PX = 64f
