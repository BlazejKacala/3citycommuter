package pl.bkacala.threecitycommuter.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import pl.bkacala.threecitycommuter.model.stops.BusStopTypeEntity

@Dao
interface BusStopsTypesDao {

    @Upsert
    fun upsertBusStopsTypes(types: List<BusStopTypeEntity>)

    @Query("SELECT * FROM bus_stops_types")
    fun getBusStopsTypes() : List<BusStopTypeEntity>
}