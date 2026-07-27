package pl.bkacala.threecitycommuter.model.gdynia

import kotlinx.serialization.Serializable

@Serializable
internal data class GdyniaShapeIndex(
    val generatedAtUtc: String,
    val sourceTrips: String,
    val sourceGtfs: String,
    val tripShapes: List<GdyniaTripShapeIndexEntry>,
    val shapeRoutes: List<GdyniaShapeRouteIndexEntry>,
)

@Serializable
internal data class GdyniaTripShapeIndexEntry(
    val tripId: Int,
    val shapeId: Int,
)

@Serializable
internal data class GdyniaShapeRouteIndexEntry(
    val shapeId: Int,
    val points: List<GdyniaRoutePointIndexEntry>,
)

@Serializable
internal data class GdyniaRoutePointIndexEntry(
    val latitude: Double,
    val longitude: Double,
)
