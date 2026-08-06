package pl.bkacala.threecitycommuter.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import pl.bkacala.threecitycommuter.model.stops.TransitStopEntity

@Dao
interface TransitStopsDao {

    @Upsert
    suspend fun upsertTransitStops(stops: List<TransitStopEntity>)

    @Query("SELECT * FROM transit_stops WHERE virtual = 0 ORDER BY provider, sourceStopId")
    suspend fun getRealTransitStops(): List<TransitStopEntity>
}
