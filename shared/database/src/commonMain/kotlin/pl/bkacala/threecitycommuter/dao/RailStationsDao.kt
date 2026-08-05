package pl.bkacala.threecitycommuter.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import pl.bkacala.threecitycommuter.model.rail.RailStationEntity

@Dao
interface RailStationsDao {

    @Upsert
    suspend fun upsertRailStations(stations: List<RailStationEntity>)

    @Query("SELECT COUNT(*) FROM rail_stations")
    suspend fun count(): Int

    @Query("SELECT * FROM rail_stations WHERE isActive = 1 ORDER BY network, name")
    suspend fun getActiveRailStations(): List<RailStationEntity>
}
