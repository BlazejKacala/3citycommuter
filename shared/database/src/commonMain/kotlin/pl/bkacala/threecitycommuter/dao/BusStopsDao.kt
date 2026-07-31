package pl.bkacala.threecitycommuter.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import pl.bkacala.threecitycommuter.model.stops.BusStopEntity

@Dao
interface BusStopsDao {

    @Upsert
    suspend fun upsertBusStations(stops: List<BusStopEntity>)

    @Query("SELECT * FROM bus_stops WHERE virtual = 0 ORDER BY provider, sourceStopId")
    suspend fun getRealBusStations(): List<BusStopEntity>
}
