package pl.bkacala.threecitycommuter.model.stops

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "bus_stops_types"
)
data class BusStopTypeEntity(
    @PrimaryKey
    val busStopId: Int,
    val isForBuses: Boolean,
    val isForTrams: Boolean
)