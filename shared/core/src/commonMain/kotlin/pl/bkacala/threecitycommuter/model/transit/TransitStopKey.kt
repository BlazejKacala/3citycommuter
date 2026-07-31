package pl.bkacala.threecitycommuter.model.transit

import kotlinx.serialization.Serializable

@Serializable
data class TransitStopKey(
    val provider: TransitProvider,
    val sourceStopId: Int,
)
