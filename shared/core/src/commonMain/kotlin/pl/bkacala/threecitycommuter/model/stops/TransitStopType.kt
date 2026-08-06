package pl.bkacala.threecitycommuter.model.stops

import kotlinx.serialization.Serializable
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

@Serializable
data class TransitStopType(
    val stopKey: TransitStopKey,
    val isForBuses: Boolean,
    val isForTrams: Boolean,
)
