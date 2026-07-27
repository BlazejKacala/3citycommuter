package pl.bkacala.threecitycommuter.repository.vehicles

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition

interface VehiclesRepository {

    suspend fun updateVehiclesBase()

    fun getVehicle(id: Int): Flow<Vehicle>

    fun getVehicles(provider: TransitProvider): Flow<List<Vehicle>>

    fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): Flow<VehiclePosition?>
}
