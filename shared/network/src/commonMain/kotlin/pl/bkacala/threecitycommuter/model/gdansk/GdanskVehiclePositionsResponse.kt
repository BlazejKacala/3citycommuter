package pl.bkacala.threecitycommuter.model.gdansk

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GdanskVehiclePositionsResponse(
    @SerialName("vehicles") val vehiclePositions: List<GdanskVehiclePositionResponse>,
)

@Serializable
data class GdanskVehiclePositionResponse(
    @SerialName("generated") val generated: Instant,
    @SerialName("routeShortName") val routeShortName: String?,
    @SerialName("tripId") val tripId: Int?,
    @SerialName("headsign") val headsign: String?,
    @SerialName("vehicleCode") val vehicleCode: String?,
    @SerialName("vehicleService") val vehicleService: String?,
    @SerialName("vehicleId") val vehicleId: Int,
    @SerialName("speed") val speed: Int?,
    @SerialName("direction") val direction: Int?,
    @SerialName("delay") val delay: Int?,
    @SerialName("scheduledTripStartTime") val scheduledTripStartTime: Instant?,
    @SerialName("lat") val lat: Double,
    @SerialName("lon") val lon: Double,
    @SerialName("gpsQuality") val gpsQuality: Int?,
)
