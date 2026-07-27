package pl.bkacala.threecitycommuter.model.gdynia

import kotlinx.serialization.Serializable

@Serializable
data class GdyniaDelayResponse(
    val lastUpdate: String,
    val delay: List<GdyniaDepartureNetworkData>,
)

@Serializable
data class GdyniaDepartureNetworkData(
    val id: String,
    val delayInSeconds: Int? = null,
    val estimatedTime: String? = null,
    val headsign: String? = null,
    val routeId: Int,
    val tripId: Int,
    val status: String? = null,
    val theoreticalTime: String? = null,
    val timestamp: String? = null,
    val trip: Long? = null,
    val vehicleCode: Int? = null,
    val vehicleId: Long? = null,
)
