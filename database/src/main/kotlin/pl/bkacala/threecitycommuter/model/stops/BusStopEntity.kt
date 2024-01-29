package pl.bkacala.threecitycommuter.model.stops

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "bus_stops"
)
data class BusStopEntity(
    @PrimaryKey
    val stopId: Int,
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
)