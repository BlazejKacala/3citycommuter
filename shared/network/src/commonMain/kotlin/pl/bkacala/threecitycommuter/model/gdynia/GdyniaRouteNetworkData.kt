package pl.bkacala.threecitycommuter.model.gdynia

import kotlinx.serialization.Serializable

@Serializable
data class GdyniaRouteNetworkData(
    val routeId: Int,
    val routeShortName: String? = null,
)
