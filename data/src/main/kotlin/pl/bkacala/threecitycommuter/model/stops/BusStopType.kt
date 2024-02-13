package pl.bkacala.threecitycommuter.model.stops

import kotlinx.serialization.Serializable

@Serializable
class BusStopType(
    val stopId: Int,
    val isForBuses: Boolean,
    val isForTrams: Boolean
)