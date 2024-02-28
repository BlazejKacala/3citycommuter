package pl.bkacala.threecitycommuter.model.departures

import kotlinx.datetime.Instant

data class Departure(
    val id: String,
    val delayInSeconds: Int?,
    val estimatedTime: Instant?,
    val headsign: String?,
    val routeId: Int,
    val scheduledTripStartTime: Instant?,
    val tripId: Int,
    val status: String?,
    val theoreticalTime: Instant?,
    val timestamp: Instant?,
    val trip: Long?,
    val vehicleCode: Int?,
    val vehicleId: Long?,
    val vehicleService: String?
)