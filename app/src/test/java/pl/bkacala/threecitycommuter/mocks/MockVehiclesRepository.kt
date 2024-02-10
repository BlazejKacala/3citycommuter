package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.tools.makeRandomInstance

object MockVehiclesRepository {
    val mockVehiclesRepository = object: VehiclesRepository {

        override suspend fun updateVehiclesBase() {

        }

        override fun getVehicle(id: Int): Flow<Vehicle> {
            return flow {
                delay(100)
                emit(makeRandomInstance<Vehicle>().copy(vehicleCode = "1"))
            }
        }

        override fun vehicles(): Flow<List<Vehicle>> {
            return flow {
                delay(100)
                emit(listOf(makeRandomInstance<Vehicle>().copy(vehicleCode = "1")))
            }
        }
    }
}