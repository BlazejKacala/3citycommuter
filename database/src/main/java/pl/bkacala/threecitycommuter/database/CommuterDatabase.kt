package pl.bkacala.threecitycommuter.database

import androidx.room.Database
import androidx.room.RoomDatabase
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.model.stops.BusStopEntity

@Database(
    entities = [BusStopEntity::class],
    version = 1
)
abstract class CommuterDatabase: RoomDatabase() {
   abstract val busStopsDao: BusStopsDao
}