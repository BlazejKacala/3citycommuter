package pl.bkacala.threecitycommuter.model.stops

import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopResponse
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

fun BusStopEntity.toStopData(): BusStopData {
    return BusStopData(
        stopKey = TransitStopKey(
            provider = TransitProvider.valueOf(this.provider),
            sourceStopId = this.sourceStopId,
        ),
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
        onDemand = this.onDemand == 1,
        activationDate = this.activationDate,
        stopLat = this.stopLat,
        stopLon = this.stopLon,
        stopUrl = this.stopUrl,
        locationType = this.locationType,
        parentStation = this.parentStation,
        stopTimezone = this.stopTimezone,
        wheelchairBoarding = this.wheelchairBoarding,
        isForBuses = this.isForBuses,
        isForTrams = this.isForTrams,
    )
}

fun GdanskStopResponse.toBusStopData(
    isForBuses: Boolean,
    isForTrams: Boolean,
): BusStopData = toEntity(isForBuses, isForTrams).toStopData()

fun GdanskStopResponse.toEntity(
    isForBuses: Boolean = true,
    isForTrams: Boolean = true,
): BusStopEntity {
    return BusStopEntity(
        provider = TransitProvider.GDANSK.name,
        sourceStopId = this.stopId,
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
        wheelchairBoarding = this.wheelchairBoarding,
        isForBuses = isForBuses,
        isForTrams = isForTrams,
    )
}

fun BusStopData.toEntity(): BusStopEntity {
    return BusStopEntity(
        provider = this.provider.name,
        sourceStopId = this.sourceStopId,
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
        onDemand = if (this.onDemand) 1 else 0,
        activationDate = this.activationDate,
        stopLat = this.stopLat,
        stopLon = this.stopLon,
        stopUrl = this.stopUrl,
        locationType = this.locationType,
        parentStation = this.parentStation,
        stopTimezone = this.stopTimezone,
        wheelchairBoarding = this.wheelchairBoarding,
        isForBuses = this.isForBuses,
        isForTrams = this.isForTrams,
    )
}
