package pl.bkacala.threecitycommuter.model.stops

import kotlinx.serialization.Serializable

@Serializable
data class BusStopType(
    val stopId: Int,
    val isForBuses: Boolean,
    val isForTrams: Boolean,
)
