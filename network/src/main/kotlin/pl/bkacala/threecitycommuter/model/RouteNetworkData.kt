package pl.bkacala.threecitycommuter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteNetworkData(
    @SerialName("coordinates")
    val coordinates: List<List<Double>>,
)