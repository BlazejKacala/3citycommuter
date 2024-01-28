package pl.bkacala.threecitycommuter.model.departures

import pl.bkacala.threecitycommuter.model.DepartureNetworkData

fun DepartureNetworkData.toDepartureData() : Departure {
    return Departure(
        id = this.id,
        delayInSeconds = this.delayInSeconds,
        estimatedTime = this.estimatedTime,
        headsign = this.headsign,
        routeId = this.routeId,
        scheduledTripStartTime = this.scheduledTripStartTime,
        tripId = this.tripId,
        status = this.status,
        theoreticalTime = this.theoreticalTime,
        timestamp = this.timestamp,
        trip = this.trip,
        vehicleCode = this.vehicleCode,
        vehicleId = this.vehicleId,
        vehicleService = this.vehicleService
    )
}