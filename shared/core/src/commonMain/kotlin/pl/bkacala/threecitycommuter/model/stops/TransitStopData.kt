package pl.bkacala.threecitycommuter.model.stops

import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.rail.RailNetwork

data class TransitStopData(
    val stopKey: TransitStopKey,
    val stopCode: String?,
    val stopName: String?,
    val stopShortName: String?,
    val stopDesc: String?,
    val subName: String?,
    val date: String?,
    val zoneId: Int,
    val zoneName: String?,
    val virtual: Int,
    val nonpassenger: Int,
    val depot: Int,
    val ticketZoneBorder: Int,
    val onDemand: Boolean,
    val activationDate: String?,
    val stopLat: Double,
    val stopLon: Double,
    val stopUrl: String?,
    val locationType: String?,
    val parentStation: String?,
    val stopTimezone: String?,
    val wheelchairBoarding: String?,
    val isForBuses: Boolean,
    val isForTrams: Boolean,
    val name: String = stopName ?: stopShortName ?: stopDesc ?: "",
    val railNetwork: RailNetwork? = null,
) {
    val provider: TransitProvider
        get() = stopKey.provider

    val sourceStopId: Int
        get() = stopKey.sourceStopId
}
