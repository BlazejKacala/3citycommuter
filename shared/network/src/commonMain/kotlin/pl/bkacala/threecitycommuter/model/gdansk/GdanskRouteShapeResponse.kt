package pl.bkacala.threecitycommuter.model.gdansk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GdanskRouteShapeResponse(
    @SerialName("coordinates")
    val coordinates: List<List<Double>>,
)
