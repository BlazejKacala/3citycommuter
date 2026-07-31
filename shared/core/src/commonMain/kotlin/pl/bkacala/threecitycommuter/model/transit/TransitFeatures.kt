package pl.bkacala.threecitycommuter.model.transit

import kotlinx.serialization.Serializable

data class TransitFeatures(
    val provider: TransitProvider,
    val supportsLiveVehicleTracking: Boolean,
    val supportsRouteShapes: Boolean,
    val supportsVehicleMetadata: Boolean,
)

@Serializable
enum class TransitProvider {
    GDANSK,
    GDYNIA,
    SKM,
}

val TransitProvider.supportsLiveVehicleTracking: Boolean
    get() = this == TransitProvider.GDANSK

val TransitProvider.supportsRouteShapes: Boolean
    get() = true

val TransitProvider.supportsVehicleMetadata: Boolean
    get() = this == TransitProvider.GDANSK
