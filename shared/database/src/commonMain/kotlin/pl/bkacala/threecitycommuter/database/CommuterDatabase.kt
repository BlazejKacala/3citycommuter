package pl.bkacala.threecitycommuter.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import pl.bkacala.threecitycommuter.dao.TransitStopsDao
import pl.bkacala.threecitycommuter.dao.RailStationsDao
import pl.bkacala.threecitycommuter.dao.VehiclesDao
import pl.bkacala.threecitycommuter.model.rail.RailStationEntity
import pl.bkacala.threecitycommuter.model.stops.TransitStopEntity
import pl.bkacala.threecitycommuter.model.vehicles.VehicleEntity

@Database(
    entities = [TransitStopEntity::class, VehicleEntity::class, RailStationEntity::class],
    version = 5,
)
@ConstructedBy(CommuterDatabaseConstructor::class)
abstract class CommuterDatabase : RoomDatabase() {
    abstract val transitStopsDao: TransitStopsDao
    abstract val vehiclesDao: VehiclesDao
    abstract val railStationsDao: RailStationsDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object CommuterDatabaseConstructor : RoomDatabaseConstructor<CommuterDatabase> {
    override fun initialize(): CommuterDatabase
}
