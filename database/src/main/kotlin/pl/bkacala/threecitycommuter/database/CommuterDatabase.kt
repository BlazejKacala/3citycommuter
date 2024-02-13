package pl.bkacala.threecitycommuter.database

import androidx.room.Database
import androidx.room.RoomDatabase
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.dao.BusStopsTypesDao
import pl.bkacala.threecitycommuter.dao.VehiclesDao
import pl.bkacala.threecitycommuter.model.stops.BusStopEntity
import pl.bkacala.threecitycommuter.model.stops.BusStopTypeEntity
import pl.bkacala.threecitycommuter.model.vehicles.VehicleEntity

@Database(
    entities = [BusStopEntity::class, VehicleEntity::class, BusStopTypeEntity::class],
    version = 1
)
abstract class CommuterDatabase : RoomDatabase() {
    abstract val busStopsDao: BusStopsDao
    abstract val vehiclesDao: VehiclesDao
    abstract val busStopTypeDao: BusStopsTypesDao
}