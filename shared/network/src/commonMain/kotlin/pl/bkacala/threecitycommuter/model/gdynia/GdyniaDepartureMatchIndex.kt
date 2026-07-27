package pl.bkacala.threecitycommuter.model.gdynia

import kotlinx.serialization.Serializable

@Serializable
internal data class GdyniaDepartureMatchIndex(
    val generatedAtUtc: String,
    val sourceGtfs: String,
    val stopTimeIndex: List<GdyniaStopTimeIndexEntry>,
)

@Serializable
internal data class GdyniaStopTimeIndexEntry(
    val stopId: Int,
    val departures: List<GdyniaStopTimeMatchIndexEntry>,
)

@Serializable
internal data class GdyniaStopTimeMatchIndexEntry(
    val time: String,
    val tripId: Int,
    val headsign: String? = null,
)
