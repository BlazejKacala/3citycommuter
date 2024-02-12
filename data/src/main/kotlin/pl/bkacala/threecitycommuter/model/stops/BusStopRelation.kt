package pl.bkacala.threecitycommuter.model.stops

import kotlinx.serialization.Serializable

@Serializable
internal class BusStopRelation(
    val stopId: Int,
    val isForBuses: Boolean,
    val isForTrams: Boolean
)