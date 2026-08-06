package pl.bkacala.threecitycommuter.model.plk

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class PlkStationsResponse(
    val generatedAt: Instant,
    val stations: List<PlkStationDto> = emptyList(),
    val totalCount: Int,
    val returnedCount: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
)

@Serializable
internal data class PlkStationDto(
    val id: Int,
    val name: String? = null,
)
