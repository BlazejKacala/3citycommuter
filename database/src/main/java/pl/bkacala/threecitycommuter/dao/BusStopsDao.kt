package pl.bkacala.threecitycommuter.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import pl.bkacala.threecitycommuter.model.stops.BusStopEntity

@Dao
interface BusStopsDao {

    @Upsert
    fun upsertBusStations(stops: List<BusStopEntity>)

    @Query("SELECT * FROM bus_stops")
    suspend fun getBusStations() : List<BusStopEntity>
}