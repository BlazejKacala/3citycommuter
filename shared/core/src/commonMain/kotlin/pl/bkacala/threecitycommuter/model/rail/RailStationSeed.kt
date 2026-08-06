package pl.bkacala.threecitycommuter.model.rail

import kotlinx.serialization.Serializable

@Serializable
data class RailStationSeed(
    val plkStationId: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val network: RailNetwork,
    val isActive: Boolean = true,
)

@Serializable
enum class RailNetwork {
    SKM,
    PKM,
}
