package pl.bkacala.threecitycommuter.repository.vehicles

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle

interface VehiclesRepository {

    suspend fun updateVehiclesBase()

    fun getVehicle(id: Int) : Flow<Vehicle>
}