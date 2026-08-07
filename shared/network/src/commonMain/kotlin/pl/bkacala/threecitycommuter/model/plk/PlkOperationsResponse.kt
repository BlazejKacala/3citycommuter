package pl.bkacala.threecitycommuter.model.plk

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class PlkOperationsResponse(
    val generatedAt: Instant? = null,
    val trains: List<PlkTrainOperationDto> = emptyList(),
    val stations: Map<String, String> = emptyMap(),
)

@Serializable
internal data class PlkTrainOperationDto(
    val scheduleId: Int,
    val orderId: Int,
    val trainOrderId: Int? = null,
    val operatingDate: String,
    val trainStatus: String? = null,
    val stations: List<PlkOperationStationDto> = emptyList(),
)

@Serializable
internal data class PlkOperationStationDto(
    val stationId: Int,
    val plannedSequenceNumber: Int? = null,
    val actualSequenceNumber: Int,
    val plannedArrival: String? = null,
    val plannedDeparture: String? = null,
    val arrivalDelayMinutes: Int? = null,
    val departureDelayMinutes: Int? = null,
    val actualArrival: String? = null,
    val actualDeparture: String? = null,
    val isConfirmed: Boolean = false,
    val isCancelled: Boolean = false,
)
