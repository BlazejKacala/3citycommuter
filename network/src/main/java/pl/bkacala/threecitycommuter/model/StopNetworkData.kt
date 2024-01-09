package pl.bkacala.threecitycommuter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class StopsNetworkData(
    @SerialName("lastUpdate") val lastUpdate: String,
    @SerialName("stops") val stops: List<StopNetworkData>
) {
    @Serializable
    data class StopNetworkData(
        @SerialName("stopId") val stopId: Int,
        @SerialName("stopCode") val stopCode: String,
        @SerialName("stopName") val stopName: String,
        @SerialName("stopShortName") val stopShortName: String,
        @SerialName("stopDesc") val stopDesc: String,
        @SerialName("subName") val subName: String,
        @SerialName("date") val date: String,
        @SerialName("zoneId") val zoneId: Int,
        @SerialName("zoneName") val zoneName: String,
        @SerialName("virtual") val virtual: Int,
        @SerialName("nonpassenger") val nonpassenger: Int,
        @SerialName("depot") val depot: Int,
        @SerialName("ticketZoneBorder") val ticketZoneBorder: Int,
        @SerialName("onDemand") val onDemand: Int,
        @SerialName("activationDate") val activationDate: String,
        @SerialName("stopLat") val stopLat: Double,
        @SerialName("stopLon") val stopLon: Double,
        @SerialName("stopUrl") val stopUrl: String?,
        @SerialName("locationType") val locationType: String?,
        @SerialName("parentStation") val parentStation: String?,
        @SerialName("stopTimezone") val stopTimezone: String,
        @SerialName("wheelchairBoarding") val wheelchairBoarding: String?,
    )
}
