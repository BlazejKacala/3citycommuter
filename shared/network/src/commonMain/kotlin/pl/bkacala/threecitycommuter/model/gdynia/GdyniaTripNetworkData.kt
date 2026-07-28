package pl.bkacala.threecitycommuter.model.gdynia

import kotlinx.serialization.Serializable

@Serializable
data class GdyniaTripNetworkData(
    val routeId: Int,
    val tripId: Int,
    val shapeId: Int,
)
