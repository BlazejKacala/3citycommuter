package pl.bkacala.threecitycommuter.model.stops

import androidx.room.Entity

@Entity(
    tableName = "transit_stops",
    primaryKeys = ["provider", "sourceStopId"],
)
data class TransitStopEntity(
    val provider: String,
    val sourceStopId: Int,
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
    val onDemand: Int,
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
)
