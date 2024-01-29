package pl.bkacala.threecitycommuter.model.stops

import pl.bkacala.threecitycommuter.model.BusStopsNetworkData


fun BusStopEntity.toStopData(): BusStopData {
    return BusStopData(
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

fun BusStopsNetworkData.BusStopNetworkData.toEntity(): BusStopEntity {
    return BusStopEntity(
        stopId = this.stopId,
        stopCode = this.stopCode,
        stopName = this.stopName,
        stopShortName = this.stopShortName,
        stopDesc = this.stopDesc,
        subName = this.subName,
        date = this.date,
        zoneId = this.zoneId ?: -1,
        zoneName = this.zoneName,
        virtual = this.virtual ?: -1,
        nonpassenger = this.nonpassenger ?: -1,
        depot = this.depot ?: -1,
        ticketZoneBorder = this.ticketZoneBorder ?: -1,
        onDemand = this.onDemand ?: -1,
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