package pl.bkacala.threecitycommuter.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

private const val EARTH_RADIUS_METERS = 6371000.0

fun LatLng.sphericalDistance(other: LatLng): Double {
    val lat1 = latitude.toRadians()
    val lat2 = other.latitude.toRadians()
    val dLat = (other.latitude - latitude).toRadians()
    val dLng = (other.longitude - longitude).toRadians()

    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) *
        sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

private fun Double.toRadians(): Double = this * PI / 180.0
