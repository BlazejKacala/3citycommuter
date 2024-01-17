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
        zoneId = this.zoneId ?: -1,
        zoneName = this.zoneName,
        virtual = this.virtual?: -1,
        nonpassenger = this.nonpassenger?: -1,
        depot = this.depot?: -1,
        ticketZoneBorder = this.ticketZoneBorder?: -1,
        onDemand = this.onDemand?: -1,
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