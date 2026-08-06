package pl.bkacala.threecitycommuter.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.dao.RailStationsDao
import pl.bkacala.threecitycommuter.dao.VehiclesDao
import pl.bkacala.threecitycommuter.model.rail.RailStationEntity
import pl.bkacala.threecitycommuter.model.stops.BusStopEntity
import pl.bkacala.threecitycommuter.model.vehicles.VehicleEntity

@Database(
    entities = [BusStopEntity::class, VehicleEntity::class, RailStationEntity::class],
    version = 4,
)
@ConstructedBy(CommuterDatabaseConstructor::class)
abstract class CommuterDatabase : RoomDatabase() {
    abstract val busStopsDao: BusStopsDao
    abstract val vehiclesDao: VehiclesDao
    abstract val railStationsDao: RailStationsDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object CommuterDatabaseConstructor : RoomDatabaseConstructor<CommuterDatabase> {
    override fun initialize(): CommuterDatabase
}
