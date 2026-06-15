package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle
import pl.bkacala.threecitycommuter.ui.screen.map.model.MapStyle

private val DEFAULT_CENTER = LatLng(54.3520, 18.6466)
private val SELECTED_STOP_COLOR = Color(0xFF6750A4)
private val DEFAULT_STOP_COLOR = Color(0xFF1976D2)
private val ROUTE_COLOR = Color(0xFF6750A4)
private val VEHICLE_COLOR = Color(0xFF6750A4)
private val USER_LOCATION_COLOR = Color(0xFF6750A4)

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
    val center = cameraTarget ?: DEFAULT_CENTER
    val effectiveZoom = cameraZoom.coerceAtLeast(mapStyle.defaultZoom)
    
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(center.longitude, center.latitude),
            zoom = effectiveZoom.toDouble()
        )
    )

    // Update camera when target or zoom changes
    LaunchedEffect(cameraTarget, cameraZoom) {
        cameraState.animateTo(
            finalPosition = CameraPosition(
                target = Position(
                    (cameraTarget ?: DEFAULT_CENTER).longitude,
                    (cameraTarget ?: DEFAULT_CENTER).latitude
                ),
                zoom = effectiveZoom.toDouble()
            )
        )
    }

    // Remember bus stops list for click handling
    val busStopsList = remember(busStops) { busStops }

    // Convert bus stops to GeoJSON features
    val busStopFeatures = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection<Point, Map<String, Any>>(
                busStops.mapIndexed { index, stop ->
                    Feature(
                        geometry = Point(Position(stop.position.longitude, stop.position.latitude)),
                        properties = mapOf("index" to index)
                    )
                }
            )
        )
    )

    // Convert route to GeoJSON feature
    val routeFeature = route?.let { routePoints ->
        if (routePoints.size >= 2) {
            rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    FeatureCollection<LineString, Map<String, Any>>(
                        listOf(
                            Feature(
                                geometry = LineString(
                                    coordinates = routePoints.map { 
                                        Position(it.longitude, it.latitude) 
                                    }
                                ),
                                properties = emptyMap()
                            )
                        )
                    )
                )
            )
        } else null
    }

    // Convert tracked vehicle to GeoJSON feature
    val vehicleFeature = trackedVehicle?.let { vehicle ->
        rememberGeoJsonSource(
            data = GeoJsonData.Features(
                FeatureCollection<Point, Map<String, Any>>(
                    listOf(
                        Feature(
                            geometry = Point(Position(vehicle.position.longitude, vehicle.position.latitude)),
                            properties = emptyMap()
                        )
                    )
                )
            )
        )
    }

    // Convert user location to GeoJSON feature
    val userLocationFeature = userLocation?.takeIf { !it.isFixed }?.let { location ->
        rememberGeoJsonSource(
            data = GeoJsonData.Features(
                FeatureCollection<Point, Map<String, Any>>(
                    listOf(
                        Feature(
                            geometry = Point(Position(location.longitude, location.latitude)),
                            properties = emptyMap()
                        )
                    )
                )
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            baseStyle = BaseStyle.Uri(mapStyle.getUriWithToken(stadiaToken)),
            onMapClick = { _, _ -> 
                onMapClicked()
                ClickResult.Pass
            }
        ) {
            // Draw route as line
            routeFeature?.let { source ->
                LineLayer(
                    id = "route-line",
                    source = source,
                    color = const(ROUTE_COLOR),
                    width = const(3.0.dp)
                )
            }

            // Draw bus stops as circles
            CircleLayer(
                id = "bus-stops",
                source = busStopFeatures,
                color = const(DEFAULT_STOP_COLOR),
                radius = const(4.0.dp),
                onClick = { features ->
                    features.firstOrNull()?.let { feature ->
                        feature.properties?.get("index")?.toString()?.toIntOrNull()?.let { index ->
                            if (index in busStopsList.indices) {
                                onBusStopSelected(busStopsList[index])
                            }
                        }
                    }
                    ClickResult.Consume
                }
            )

            // Draw selected bus stop as larger circle
            selectedBusStop?.let { selectedStop ->
                val selectedSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection<Point, Map<String, Any>>(
                            listOf(
                                Feature(
                                    geometry = Point(Position(selectedStop.position.longitude, selectedStop.position.latitude)),
                                    properties = emptyMap()
                                )
                            )
                        )
                    )
                )
                CircleLayer(
                    id = "selected-bus-stop",
                    source = selectedSource,
                    color = const(SELECTED_STOP_COLOR),
                    radius = const(8.0.dp)
                )
            }

            // Draw tracked vehicle
            vehicleFeature?.let { source ->
                CircleLayer(
                    id = "tracked-vehicle",
                    source = source,
                    color = const(VEHICLE_COLOR),
                    radius = const(10.0.dp)
                )
            }

            // Draw user location
            userLocationFeature?.let { source ->
                CircleLayer(
                    id = "user-location-outer",
                    source = source,
                    color = const(USER_LOCATION_COLOR),
                    radius = const(8.0.dp)
                )
                CircleLayer(
                    id = "user-location-inner",
                    source = source,
                    color = const(Color.White),
                    radius = const(5.0.dp)
                )
            }
        }
    }
}

