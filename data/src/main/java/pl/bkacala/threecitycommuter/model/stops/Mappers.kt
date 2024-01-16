package pl.bkacala.threecitycommuter.model.stops

import pl.bkacala.threecitycommuter.model.StopsNetworkData

fun StopsNetworkData.StopNetworkData.toStopData(): StopData {
    return StopData(
        stopId = this.stopId,
        stopCode = this.stopCode,
        stopName = this.stopName,
        stopShortName = this.stopShortName,
        stopDesc = this.stopDesc,
        subName = this.subName,
        date = this.date,
        zoneId = this.zoneId,
        zoneName = this.zoneName,
        virtual = this.virtual,
        nonpassenger = this.nonpassenger,
        depot = this.depot,
        ticketZoneBorder = this.ticketZoneBorder,
        onDemand = this.onDemand,
        activationDate = this.activationDate,
        stopLat = this.stopLat,
        stopLon = this.stopLon,
        stopUrl = this.stopUrl,
        locationType = this.locationType,
        parentStation = this.parentStation,
        stopTimezone = this.stopTimezone,
        wheelchairBoarding = this.wheelchairBoarding
    )
}