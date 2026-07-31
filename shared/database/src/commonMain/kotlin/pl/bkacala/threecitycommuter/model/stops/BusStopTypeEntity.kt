package pl.bkacala.threecitycommuter.model.stops

import androidx.room.Entity
@Entity(
    tableName = "bus_stops_types",
    primaryKeys = ["provider", "sourceStopId"],
)
data class BusStopTypeEntity(
    val provider: String,
    val sourceStopId: Int,
    val isForBuses: Boolean,
    val isForTrams: Boolean,
)
