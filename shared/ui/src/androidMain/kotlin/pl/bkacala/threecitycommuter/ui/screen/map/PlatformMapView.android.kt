package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle

private val DEFAULT_CENTER = LatLng(54.3520, 18.6466)
private val SELECTED_STOP_COLOR = Color(0xFF6750A4)
private val DEFAULT_STOP_COLOR = Color(0xFF1976D2)
private val ROUTE_COLOR = Color(0xFF6750A4)

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
    val center = cameraTarget ?: DEFAULT_CENTER

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8))
            .pointerInput(Unit) {
                detectTapGestures {
                    onMapClicked()
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw bus stops as dots
            busStops.forEach { stop ->
                val x = ((stop.position.longitude - center.longitude) * 10000 + size.width / 2).toFloat()
                val y = (-(stop.position.latitude - center.latitude) * 10000 + size.height / 2).toFloat()

                if (x in 0f..size.width && y in 0f..size.height) {
                    val isSelected = stop == selectedBusStop
                    drawCircle(
                        color = if (isSelected) SELECTED_STOP_COLOR else DEFAULT_STOP_COLOR,
                        radius = if (isSelected) 8f else 4f,
                        center = Offset(x, y),
                    )
                }
            }

            // Draw route
            route?.let { routePoints ->
                if (routePoints.size >= 2) {
                    for (i in 0 until routePoints.size - 1) {
                        val startX = ((routePoints[i].longitude - center.longitude) * 10000 + size.width / 2).toFloat()
                        val startY = (-(routePoints[i].latitude - center.latitude) * 10000 + size.height / 2).toFloat()
                        val endX = ((routePoints[i + 1].longitude - center.longitude) * 10000 + size.width / 2).toFloat()
                        val endY = (-(routePoints[i + 1].latitude - center.latitude) * 10000 + size.height / 2).toFloat()
                        drawLine(
                            color = ROUTE_COLOR,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 3f,
                        )
                    }
                }
            }

            // Draw tracked vehicle
            trackedVehicle?.let { vehicle ->
                val vx = ((vehicle.position.longitude - center.longitude) * 10000 + size.width / 2).toFloat()
                val vy = (-(vehicle.position.latitude - center.latitude) * 10000 + size.height / 2).toFloat()
                drawCircle(
                    color = Color(0xFF6750A4),
                    radius = 10f,
                    center = Offset(vx, vy),
                )
            }

            // Draw user location
            userLocation?.let { location ->
                if (!location.isFixed) {
                    val ux = ((location.longitude - center.longitude) * 10000 + size.width / 2).toFloat()
                    val uy = (-(location.latitude - center.latitude) * 10000 + size.height / 2).toFloat()
                    drawCircle(
                        color = Color(0xFF6750A4),
                        radius = 8f,
                        center = Offset(ux, uy),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = Offset(ux, uy),
                    )
                }
            }
        }

        Text(
            text = "Android Map Preview (${center.latitude.format(4)}, ${center.longitude.format(4)})",
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Text(
            text = "${busStops.size} przystankow",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = mapBottomPadding + 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

private fun Double.format(digits: Int): String = String.format("%.${digits}f", this)

