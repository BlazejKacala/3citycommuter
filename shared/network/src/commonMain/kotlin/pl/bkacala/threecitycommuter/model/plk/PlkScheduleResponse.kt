package pl.bkacala.threecitycommuter.model.plk

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class PlkScheduleResponse(
    val generatedAt: Instant,
    val routes: List<PlkRouteDto> = emptyList(),
    val dictionaries: PlkScheduleDictionaries? = null,
)

@Serializable
internal data class PlkScheduleDictionaries(
    val stations: Map<String, PlkStationDto> = emptyMap(),
)

@Serializable
internal data class PlkRouteDto(
    val scheduleId: Int,
    val orderId: Int,
    val trainOrderId: Int? = null,
    val name: String? = null,
    val carrierCode: String? = null,
    val nationalNumber: String? = null,
    val internationalArrivalNumber: String? = null,
    val internationalDepartureNumber: String? = null,
    val commercialCategorySymbol: String? = null,
    val operatingDates: List<String> = emptyList(),
    val stations: List<PlkStationOnRouteDto> = emptyList(),
)

@Serializable
internal data class PlkStationOnRouteDto(
    val stationId: Int,
    val orderNumber: Int,
    val arrivalCommercialCategory: String? = null,
    val arrivalTrainNumber: String? = null,
    val arrivalPlatform: String? = null,
    val arrivalTrack: String? = null,
    val arrivalDay: Int? = null,
    val arrivalTime: String? = null,
    val departureCommercialCategory: String? = null,
    val departureTrainNumber: String? = null,
    val departurePlatform: String? = null,
    val departureTrack: String? = null,
    val departureDay: Int? = null,
    val departureTime: String? = null,
    val stopTypeId: Int? = null,
    val stopTypeName: String? = null,
)
