package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Train
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
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.sphericalDistance
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.component.TransitStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.VehicleType
import pl.bkacala.threecitycommuter.ui.theme.stopMarkerColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow

internal data class StopMapMarker(
    val position: LatLng,
    val stop: TransitStopMapItem?,
    val count: Int,
    val provider: TransitProvider?,
)

internal fun List<TransitStopMapItem>.toStopMapMarkers(
    zoom: Int,
    cluster: Boolean = true,
): List<StopMapMarker> {
    if (!cluster || zoom >= STOPS_UNCLUSTERED_ZOOM) {
        return map { stop ->
            StopMapMarker(
                position = stop.position,
                stop = stop,
                count = 1,
                provider = stop.data.provider,
            )
        }
    }

    val stopsWithIndex = withIndex().toList()
    val (skmStops, otherStops) = stopsWithIndex.partition { it.value.data.provider == TransitProvider.PLK }
    val longitudeCellSize = 360.0 / 2.0.pow(zoom) * CLUSTER_RADIUS_TILE_FRACTION
    val latitudeCellSize = longitudeCellSize * cos(MAP_REFERENCE_LATITUDE * PI / 180.0)
    val groups = otherStops.groupBy { indexedStop ->
        Pair(
            floor(indexedStop.value.position.longitude / longitudeCellSize).toLong(),
            floor(indexedStop.value.position.latitude / latitudeCellSize).toLong(),
        )
    }.values + skmStops.map { listOf(it) }

    return groups.map { group ->
        if (group.size == 1) {
            val stop = group.first().value
            StopMapMarker(stop.position, stop, 1, stop.data.provider)
        } else {
            StopMapMarker(
                position = LatLng(
                    latitude = group.sumOf { it.value.position.latitude } / group.size,
                    longitude = group.sumOf { it.value.position.longitude } / group.size,
                ),
                stop = null,
                count = group.size,
                provider = group.groupingBy { it.value.data.provider }.eachCount().maxByOrNull { it.value }?.key,
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
    selectedTransitStop: TransitStopMapItem?,
    trackedVehicle: TrackedVehicle?,
    vehicleColor: Color,
    routeStopTimes: Map<TransitStopKey, Instant?>?,
    currentRouteStopKey: TransitStopKey?,
) {
    val busPainter = rememberVectorPainter(Icons.Outlined.DirectionsBus)
    val tramPainter = rememberVectorPainter(Icons.Outlined.Tram)
    val trainPainter = rememberVectorPainter(Icons.Outlined.Train)
    val textMeasurer = rememberTextMeasurer()
    val animatedVehiclePosition = remember { Animatable(Offset.Zero, OffsetVectorConverter) }
    var animatedVehicleNumber by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf(Clock.System.now()) }
    val countTextStyle = remember {
        TextStyle(color = Color.White, fontSize = CLUSTER_COUNT_TEXT_SIZE, fontWeight = FontWeight.Bold)
    }
    val vehicleTextStyle = remember {
        TextStyle(color = Color.White, fontSize = VEHICLE_TEXT_SIZE, fontWeight = FontWeight.Bold)
    }
    val routeTimeTextStyle = remember {
        TextStyle(color = Color.Black, fontSize = ROUTE_TIME_TEXT_SIZE, fontWeight = FontWeight.Bold)
    }

    LaunchedEffect(routeStopTimes) {
        if (routeStopTimes == null) return@LaunchedEffect
        while (true) {
            currentTime = Clock.System.now()
            delay(ROUTE_TIME_REFRESH_INTERVAL_MS)
        }
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
            if (marker.stop != null && marker.stop.key == selectedTransitStop?.key) return@forEach

            val location = projection.screenLocationFromPosition(marker.position.toPosition())
            val center = Offset(location.x.toPx(), location.y.toPx())
            if (!center.isInside(size)) return@forEach

            val isCluster = marker.stop == null
            val markerColor = stopMarkerColor(marker.provider)
            if (isCluster) {
                drawCircle(
                    color = Color.White,
                    radius = CLUSTER_OUTER_RADIUS.toPx(),
                    center = center,
                )
                drawCircle(
                    color = markerColor,
                    radius = CLUSTER_INNER_RADIUS.toPx(),
                    center = center,
                )
            } else {
                val isRailStation = marker.stop.getStationType() == TransitStopMapItem.Type.Train
                val markerSize = if (isRailStation) RAIL_STOP_MARKER_SIZE else STOP_MARKER_SIZE
                val outerSize = Size(markerSize.toPx(), markerSize.toPx())
                val innerMarkerSize = markerSize - STOP_MARKER_BORDER
                val innerSize = Size(innerMarkerSize.toPx(), innerMarkerSize.toPx())
                drawRoundRect(
                    color = Color.White,
                    topLeft = center - Offset(outerSize.width / 2f, outerSize.height / 2f),
                    size = outerSize,
                    cornerRadius = CornerRadius(STOP_MARKER_CORNER_RADIUS.toPx()),
                )
                drawRoundRect(
                    color = markerColor,
                    topLeft = center - Offset(innerSize.width / 2f, innerSize.height / 2f),
                    size = innerSize,
                    cornerRadius = CornerRadius(STOP_MARKER_CORNER_RADIUS.toPx()),
                )
            }

            if (isCluster) {
                val count = if (marker.count < CLUSTER_COUNT_OVERFLOW_THRESHOLD) {
                    marker.count.toString()
                } else {
                    CLUSTER_COUNT_OVERFLOW_LABEL
                }
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
                    TransitStopMapItem.Type.Tram -> tramPainter
                    TransitStopMapItem.Type.Train -> trainPainter
                    else -> busPainter
                }
                val iconSize = if (marker.stop.getStationType() == TransitStopMapItem.Type.Train) {
                    RAIL_STOP_ICON_SIZE
                } else {
                    STOP_ICON_SIZE
                }.toPx()
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
                routeStopTimes?.let { times ->
                    val label = formatRouteStopTime(
                        expectedTime = times[marker.stop.key],
                        now = currentTime,
                        isCurrentStop = marker.stop.key == currentRouteStopKey,
                    )
                    val text = textMeasurer.measure(label, routeTimeTextStyle)
                    val horizontalPadding = ROUTE_TIME_HORIZONTAL_PADDING.toPx()
                    val verticalPadding = ROUTE_TIME_VERTICAL_PADDING.toPx()
                    val bubbleSize = Size(
                        width = text.size.width + horizontalPadding * 2,
                        height = text.size.height + verticalPadding * 2,
                    )
                    val bubbleTopLeft = Offset(
                        x = center.x - bubbleSize.width / 2f,
                        y = center.y -
                            (
                                if (marker.stop.getStationType() == TransitStopMapItem.Type.Train) {
                                    RAIL_STOP_MARKER_SIZE
                                } else {
                                    STOP_MARKER_SIZE
                                }
                                ).toPx() / 2f -
                            bubbleSize.height - ROUTE_TIME_GAP.toPx(),
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = bubbleTopLeft,
                        size = bubbleSize,
                        cornerRadius = CornerRadius(bubbleSize.height / 2f),
                    )
                    drawText(
                        textLayoutResult = text,
                        topLeft = bubbleTopLeft + Offset(horizontalPadding, verticalPadding),
                    )
                }
            }
        }

        selectedTransitStop?.let { stop ->
            val location = projection.screenLocationFromPosition(stop.position.toPosition())
            val center = Offset(location.x.toPx(), location.y.toPx())
            if (center.isInside(size)) {
                val selectedStopColor = stopMarkerColor(stop.data.provider, isSelected = true)
                val markerColor = stopMarkerColor(stop.data.provider)
                val isRailStation = stop.getStationType() == TransitStopMapItem.Type.Train
                val markerSize = if (isRailStation) {
                    RAIL_STOP_MARKER_SIZE
                } else {
                    STOP_MARKER_SIZE
                }
                val outerSize = Size(markerSize.toPx(), markerSize.toPx())
                val selectedOuterSize = markerSize + SELECTED_STOP_BORDER * 2
                val selectedOuter = Size(selectedOuterSize.toPx(), selectedOuterSize.toPx())
                val innerMarkerSize = markerSize - STOP_MARKER_BORDER
                val innerSize = Size(innerMarkerSize.toPx(), innerMarkerSize.toPx())
                drawRoundRect(
                    color = selectedStopColor,
                    topLeft = center - Offset(selectedOuter.width / 2f, selectedOuter.height / 2f),
                    size = selectedOuter,
                    cornerRadius = CornerRadius(SELECTED_STOP_MARKER_CORNER_RADIUS.toPx()),
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = center - Offset(outerSize.width / 2f, outerSize.height / 2f),
                    size = outerSize,
                    cornerRadius = CornerRadius(STOP_MARKER_CORNER_RADIUS.toPx()),
                )
                drawRoundRect(
                    color = markerColor,
                    topLeft = center - Offset(innerSize.width / 2f, innerSize.height / 2f),
                    size = innerSize,
                    cornerRadius = CornerRadius(STOP_MARKER_CORNER_RADIUS.toPx()),
                )
                val painter = when (stop.getStationType()) {
                    TransitStopMapItem.Type.Tram -> tramPainter
                    TransitStopMapItem.Type.Train -> trainPainter
                    else -> busPainter
                }
                val iconSize = (if (isRailStation) RAIL_STOP_ICON_SIZE else STOP_ICON_SIZE).toPx()
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
                routeStopTimes?.let { times ->
                    val label = formatRouteStopTime(
                        expectedTime = times[stop.key],
                        now = currentTime,
                        isCurrentStop = stop.key == currentRouteStopKey,
                    )
                    val text = textMeasurer.measure(label, routeTimeTextStyle)
                    val horizontalPadding = ROUTE_TIME_HORIZONTAL_PADDING.toPx()
                    val verticalPadding = ROUTE_TIME_VERTICAL_PADDING.toPx()
                    val bubbleSize = Size(
                        width = text.size.width + horizontalPadding * 2,
                        height = text.size.height + verticalPadding * 2,
                    )
                    val bubbleTopLeft = Offset(
                        x = center.x - bubbleSize.width / 2f,
                        y = center.y - markerSize.toPx() / 2f - bubbleSize.height - ROUTE_TIME_GAP.toPx(),
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = bubbleTopLeft,
                        size = bubbleSize,
                        cornerRadius = CornerRadius(bubbleSize.height / 2f),
                    )
                    drawText(
                        textLayoutResult = text,
                        topLeft = bubbleTopLeft + Offset(horizontalPadding, verticalPadding),
                    )
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
                    VehicleType.Train -> trainPainter
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

// Clustering
private const val STOPS_UNCLUSTERED_ZOOM = 16
private const val CLUSTER_RADIUS_TILE_FRACTION = 72.0 / 512.0
private const val MAP_REFERENCE_LATITUDE = 54.352

// Cluster markers
private val CLUSTER_OUTER_RADIUS = 20.dp
private val CLUSTER_INNER_RADIUS = 15.dp
private val CLUSTER_COUNT_TEXT_SIZE = 12.sp
private const val CLUSTER_COUNT_OVERFLOW_THRESHOLD = 100
private const val CLUSTER_COUNT_OVERFLOW_LABEL = "99+"

// Stop markers
private val STOP_MARKER_SIZE = 28.dp
private val RAIL_STOP_MARKER_SIZE = 34.dp
private val STOP_MARKER_BORDER = 4.dp
private val STOP_MARKER_CORNER_RADIUS = 6.dp
private val STOP_ICON_SIZE = 20.dp
private val SELECTED_STOP_BORDER = 3.dp
private val SELECTED_STOP_MARKER_CORNER_RADIUS = 7.dp
private val RAIL_STOP_ICON_SIZE = 24.dp

// Vehicle markers
private val VEHICLE_MARKER_HEIGHT = 36.dp
private val VEHICLE_ICON_SIZE = 20.dp
private val VEHICLE_HORIZONTAL_PADDING = 10.dp
private val VEHICLE_CONTENT_GAP = 4.dp
private val VEHICLE_BORDER_WIDTH = 3.dp
private val VEHICLE_TEXT_SIZE = 12.sp

// Route stop time labels
private val ROUTE_TIME_TEXT_SIZE = 10.sp
private val ROUTE_TIME_HORIZONTAL_PADDING = 6.dp
private val ROUTE_TIME_VERTICAL_PADDING = 3.dp
private val ROUTE_TIME_GAP = 4.dp
private const val ROUTE_TIME_REFRESH_INTERVAL_MS = 30_000L

// Interaction
private const val MARKER_CLICK_RADIUS_DP = 56f
private const val MIN_MARKER_CLICK_RADIUS_METERS = 15.0
private const val MARKER_EDGE_MARGIN_PX = 64f

// Animation
private const val VEHICLE_POSITION_ANIMATION_DURATION_MS = 9_000

internal fun formatRouteStopTime(
    expectedTime: Instant?,
    now: Instant,
    isCurrentStop: Boolean = false,
): String {
    if (isCurrentStop) return "na przystanku"
    if (expectedTime == null) return "brak danych"
    val seconds = expectedTime.epochSeconds - now.epochSeconds
    if (seconds <= 0) {
        val minutes = ((-seconds + 59) / 60).coerceAtLeast(1)
        return "$minutes min temu"
    }
    val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
    return "za $minutes min"
}
