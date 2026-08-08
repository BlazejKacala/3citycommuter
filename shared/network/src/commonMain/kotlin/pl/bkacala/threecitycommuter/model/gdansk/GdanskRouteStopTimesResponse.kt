package pl.bkacala.threecitycommuter.model.gdansk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GdanskRouteStopTimesResponse(
    @SerialName("lastUpdate")
    val lastUpdate: String,
    @SerialName("stopTimes")
    val stopTimes: List<GdanskRouteStopTimeResponse>,
)

@Serializable
data class GdanskRouteStopTimeResponse(
    @SerialName("routeId")
    val routeId: Int? = null,
    @SerialName("tripId")
    val tripId: Int,
    @SerialName("variantId")
    val variantId: Int? = null,
    @SerialName("stopId")
    val stopId: Int,
    @SerialName("stopSequence")
    val stopSequence: Int,
    @SerialName("arrivalTime")
    val arrivalTime: String? = null,
    @SerialName("departureTime")
    val departureTime: String? = null,
    @SerialName("passenger")
    val passenger: Boolean? = null,
    @SerialName("nonpassenger")
    val nonpassenger: Int? = null,
    @SerialName("virtual")
    val virtual: Int? = null,
)
