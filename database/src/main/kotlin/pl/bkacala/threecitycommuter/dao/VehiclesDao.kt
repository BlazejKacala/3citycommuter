package pl.bkacala.threecitycommuter.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import pl.bkacala.threecitycommuter.model.vehicles.VehicleEntity

@Dao
interface VehiclesDao {

    @Upsert
    fun upsertVehicles(vehicles: List<VehicleEntity>)

    @Query("SELECT * from vehicles WHERE vehicleCode == :vehicleCode")
    fun getVehicle(vehicleCode: Int): VehicleEntity

    @Query("SELECT * from vehicles")
    fun getVehicles(): List<VehicleEntity>
}