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
        @SerialName("stopCode") val stopCode: String? = null,
        @SerialName("stopName") val stopName: String? = null,
        @SerialName("stopShortName") val stopShortName: String? = null,
        @SerialName("stopDesc") val stopDesc: String? = null,
        @SerialName("subName") val subName: String? = null,
        @SerialName("date") val date: String? = null,
        @SerialName("zoneId") val zoneId: Int? = -1,
        @SerialName("zoneName") val zoneName: String? = null,
        @SerialName("virtual") val virtual: Int? = -1,
        @SerialName("nonpassenger") val nonpassenger: Int? = -1,
        @SerialName("depot") val depot: Int? = -1,
        @SerialName("ticketZoneBorder") val ticketZoneBorder: Int? = -1,
        @SerialName("onDemand") val onDemand: Int? = -1,
        @SerialName("activationDate") val activationDate: String? = null,
        @SerialName("stopLat") val stopLat: Double,
        @SerialName("stopLon") val stopLon: Double,
        @SerialName("stopUrl") val stopUrl: String? = null,
        @SerialName("locationType") val locationType: String? = null,
        @SerialName("parentStation") val parentStation: String? = null,
        @SerialName("stopTimezone") val stopTimezone: String? = null,
        @SerialName("wheelchairBoarding") val wheelchairBoarding: String? = null,
    )
}
