package pl.bkacala.threecitycommuter.model.gdynia

import kotlinx.serialization.Serializable

@Serializable
data class GdyniaStopNetworkData(
    val stopId: Int,
    val stopCode: String? = null,
    val stopName: String? = null,
    val stopDesc: String? = null,
    val stopLat: String,
    val stopLon: String,
    val zoneId: String,
    val stopUrl: String? = null,
    val locationType: String? = null,
    val parentStation: String? = null,
    val stopTimezone: String? = null,
    val wheelchairBoarding: String? = null,
    val ticketZoneBorder: String? = null,
)
