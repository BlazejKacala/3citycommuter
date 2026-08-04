package pl.bkacala.threecitycommuter.model.route

import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

data class Route(
    val shape: List<GeoPoint>,
    val stops: List<Stop> = emptyList(),
) {
    data class GeoPoint(
        val latitude: Double,
        val longitude: Double,
    )

    data class Stop(
        val key: TransitStopKey,
        val sequence: Int,
    )
}
