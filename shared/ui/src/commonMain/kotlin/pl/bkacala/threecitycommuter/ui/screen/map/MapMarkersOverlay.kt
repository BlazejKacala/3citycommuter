package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
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
import pl.bkacala.threecitycommuter.ui.screen.map.component.VehicleType
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
    val animatedVehiclePosition = remember { Animatable(Offset.Zero, OffsetVectorConverter) }
    var animatedVehicleNumber by remember { mutableStateOf<String?>(null) }
    val countTextStyle = remember {
        TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    val vehicleTextStyle = remember {
        TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    LaunchedEffect(trackedVehicle?.number, trackedVehicle?.position) {
        val vehicle = trackedVehicle ?: run {
            animatedVehicleNumber = null
            return@LaunchedEffect
        }
        val target = vehicle.position.toOffset()

        if (animatedVehicleNumber != vehicle.number) {
            animatedVehiclePosition.snapTo(target)
            animatedVehicleNumber = vehicle.number
        } else {
            animatedVehiclePosition.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = VEHICLE_POSITION_ANIMATION_DURATION_MS,
                    easing = LinearEasing,
                ),
            )
        }
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
            val location = projection.screenLocationFromPosition(animatedVehiclePosition.value.toLatLng().toPosition())
            val center = Offset(location.x.toPx(), location.y.toPx())
            if (center.isInside(size)) {
                val text = textMeasurer.measure(vehicle.number, vehicleTextStyle)
                val iconSize = VEHICLE_ICON_SIZE.toPx()
                val contentWidth =
                    VEHICLE_HORIZONTAL_PADDING.toPx() * 2 + iconSize + VEHICLE_CONTENT_GAP.toPx() + text.size.width
                val markerHeight = VEHICLE_MARKER_HEIGHT.toPx()
                val borderWidth = VEHICLE_BORDER_WIDTH.toPx()
                val topLeft = Offset(
                    x = center.x - (contentWidth + borderWidth * 2) / 2f,
                    y = center.y - (markerHeight + borderWidth * 2) / 2f,
                )
                val outerSize = Size(contentWidth + borderWidth * 2, markerHeight + borderWidth * 2)

                drawRoundRect(
                    color = Color.White,
                    topLeft = topLeft,
                    size = outerSize,
                    cornerRadius = CornerRadius(outerSize.height / 2f),
                )
                drawRoundRect(
                    color = vehicleColor,
                    topLeft = topLeft + Offset(borderWidth, borderWidth),
                    size = Size(contentWidth, markerHeight),
                    cornerRadius = CornerRadius(markerHeight / 2f),
                )
                val painter = when (vehicle.type) {
                    VehicleType.Tram -> tramPainter
                    VehicleType.Bus -> busPainter
                }
                translate(
                    left = topLeft.x + borderWidth + VEHICLE_HORIZONTAL_PADDING.toPx(),
                    top = center.y - iconSize / 2f,
                ) {
                    with(painter) {
                        draw(
                            size = Size(iconSize, iconSize),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(
                        topLeft.x +
                            borderWidth +
                            VEHICLE_HORIZONTAL_PADDING.toPx() +
                            iconSize +
                            VEHICLE_CONTENT_GAP.toPx(),
                        center.y - text.size.height / 2f,
                    ),
                )
            }
        }
    }
}

private fun LatLng.toPosition() = Position(longitude, latitude)

private fun LatLng.toOffset() = Offset(longitude.toFloat(), latitude.toFloat())

private fun Offset.toLatLng() = LatLng(latitude = y.toDouble(), longitude = x.toDouble())

private val OffsetVectorConverter = TwoWayConverter<Offset, AnimationVector2D>(
    convertToVector = { offset -> AnimationVector2D(offset.x, offset.y) },
    convertFromVector = { vector -> Offset(vector.v1, vector.v2) },
)

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
private val VEHICLE_MARKER_HEIGHT = 36.dp
private val VEHICLE_ICON_SIZE = 20.dp
private val VEHICLE_HORIZONTAL_PADDING = 10.dp
private val VEHICLE_CONTENT_GAP = 4.dp
private val VEHICLE_BORDER_WIDTH = 3.dp
private const val MARKER_CLICK_RADIUS_DP = 56f
private const val MIN_MARKER_CLICK_RADIUS_METERS = 15.0
private const val MARKER_EDGE_MARGIN_PX = 64f
private const val VEHICLE_POSITION_ANIMATION_DURATION_MS = 9_000
