package pl.bkacala.threecitycommuter.model.route

data class Route(
    val shape: List<GeoPoint>
) {
    data class GeoPoint(
        val latitude: Double,
        val longitude: Double
    )
}