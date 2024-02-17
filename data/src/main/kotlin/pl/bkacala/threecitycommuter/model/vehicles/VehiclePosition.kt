package pl.bkacala.threecitycommuter.model.vehicles

import kotlinx.datetime.Instant

data class VehiclePosition(
    val generated: Instant,
    val routeShortName: String?,
    val tripId: Int?,
    val headsign: String?,
    val vehicleCode: String?,
    val vehicleService: String?,
    val vehicleId: Int,
    val speed: Int?,
    val direction: Int?,
    val delay: Int?,
    val scheduledTripStartTime: Instant?,
    val lat: Double,
    val lon: Double,
    val gpsQuality: Int?
)