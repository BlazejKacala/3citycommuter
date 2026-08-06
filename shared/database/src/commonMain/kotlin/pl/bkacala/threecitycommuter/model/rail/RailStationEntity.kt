package pl.bkacala.threecitycommuter.model.rail

import androidx.room.Entity

@Entity(tableName = "rail_stations", primaryKeys = ["plkStationId"])
data class RailStationEntity(
    val plkStationId: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val network: String,
    val isActive: Boolean,
)
