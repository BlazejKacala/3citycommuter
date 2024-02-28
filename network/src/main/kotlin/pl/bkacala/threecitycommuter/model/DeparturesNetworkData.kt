package pl.bkacala.threecitycommuter.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class DepartureNetworkData(
    val id: String,
    val delayInSeconds: Int?,
    @SerialName("estimatedTime")
    val estimatedTime: Instant?,
    val headsign: String?,
    val routeId: Int,
    @SerialName("scheduledTripStartTime")
    val scheduledTripStartTime: Instant?,
    val tripId: Int,
    val status: String?,
    @SerialName("theoreticalTime")
    val theoreticalTime: Instant?,
    @SerialName("timestamp")
    val timestamp: Instant?,
    val trip: Long?,
    val vehicleCode: Int?,
    val vehicleId: Long?,
    val vehicleService: String?
)

@Serializable
data class DepartureList(
    val departures: List<DepartureNetworkData>
)

