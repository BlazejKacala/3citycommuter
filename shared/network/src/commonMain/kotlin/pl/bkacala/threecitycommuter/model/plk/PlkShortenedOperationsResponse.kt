package pl.bkacala.threecitycommuter.model.plk

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PlkShortenedOperationsResponse(
    @SerialName("ts") val generatedAt: Instant? = null,
    @SerialName("tr") val trains: List<PlkShortenedTrainOperationDto> = emptyList(),
    @SerialName("st") val stations: Map<String, String> = emptyMap(),
)

@Serializable
internal data class PlkShortenedTrainOperationDto(
    @SerialName("sid") val scheduleId: Int,
    @SerialName("oid") val orderId: Int,
    @SerialName("toid") val trainOrderId: Int,
    @SerialName("od") val operatingDate: String,
    @SerialName("s") val trainStatus: String? = null,
    @SerialName("st") val stations: List<PlkShortenedOperationStationDto> = emptyList(),
)

@Serializable
internal data class PlkShortenedOperationStationDto(
    @SerialName("id") val stationId: Int,
    @SerialName("psn") val plannedSequenceNumber: Int? = null,
    @SerialName("asn") val actualSequenceNumber: Int,
    @SerialName("pa") val plannedArrival: String? = null,
    @SerialName("pd") val plannedDeparture: String? = null,
    @SerialName("adm") val arrivalDelayMinutes: Int? = null,
    @SerialName("ddm") val departureDelayMinutes: Int? = null,
    @SerialName("aa") val actualArrival: String? = null,
    @SerialName("ad") val actualDeparture: String? = null,
    @SerialName("cf") val isConfirmed: Boolean = false,
    @SerialName("cn") val isCancelled: Boolean = false,
)

internal fun PlkShortenedOperationsResponse.toOperationsResponse(): PlkOperationsResponse =
    PlkOperationsResponse(
        generatedAt = generatedAt,
        trains = trains.map { it.toTrainOperation() },
        stations = stations,
    )

private fun PlkShortenedTrainOperationDto.toTrainOperation(): PlkTrainOperationDto =
    PlkTrainOperationDto(
        scheduleId = scheduleId,
        orderId = orderId,
        trainOrderId = trainOrderId,
        operatingDate = operatingDate,
        trainStatus = trainStatus,
        stations = stations.map { it.toOperationStation() },
    )

private fun PlkShortenedOperationStationDto.toOperationStation(): PlkOperationStationDto =
    PlkOperationStationDto(
        stationId = stationId,
        plannedSequenceNumber = plannedSequenceNumber,
        actualSequenceNumber = actualSequenceNumber,
        plannedArrival = plannedArrival,
        plannedDeparture = plannedDeparture,
        arrivalDelayMinutes = arrivalDelayMinutes,
        departureDelayMinutes = departureDelayMinutes,
        actualArrival = actualArrival,
        actualDeparture = actualDeparture,
        isConfirmed = isConfirmed,
        isCancelled = isCancelled,
    )
