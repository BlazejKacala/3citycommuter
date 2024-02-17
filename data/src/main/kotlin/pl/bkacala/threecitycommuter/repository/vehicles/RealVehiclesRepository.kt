package pl.bkacala.threecitycommuter.repository.vehicles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.dao.VehiclesDao
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.model.vehicles.toVehicle
import pl.bkacala.threecitycommuter.model.vehicles.toVehicleEntity
import pl.bkacala.threecitycommuter.model.vehicles.toVehiclePosition
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThenOneDay
import javax.inject.Inject

private const val VEHICLES = "vehicles_key"

internal class RealVehiclesRepository @Inject constructor(
    private val vehiclesDao: VehiclesDao,
    private val networkClient: NetworkClient,
    private val lastUpdateRepository: LastUpdateRepository
) : VehiclesRepository {

    override suspend fun updateVehiclesBase() {
        val networkVehicles = networkClient.getVehicles()
        vehiclesDao.upsertVehicles(networkVehicles.results.map { it.toVehicleEntity() })
    }

    override fun getVehicle(id: Int): Flow<Vehicle> {
        return flow {
            ensureUpToDate()
            emit(vehiclesDao.getVehicle(id).toVehicle())
        }.flowOn(Dispatchers.IO)
    }

    override fun getVehicles(): Flow<List<Vehicle>> {
        return flow {
            ensureUpToDate()
            emit(vehiclesDao.getVehicles().map { it.toVehicle() })
        }.flowOn(Dispatchers.IO)
    }

    override fun getVehiclePosition(vehicleId: Int): Flow<VehiclePosition?> {
        return flow {
            emit(networkClient.getVehiclesPositions().vehiclePositions
                .firstOrNull { it.vehicleId == vehicleId }
                ?.toVehiclePosition()
            )
        }
    }

    private suspend fun ensureUpToDate() {
        val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(VEHICLES)

        if (lastUpdateTimestamp.isOlderThenOneDay()) {
            vehiclesDao.upsertVehicles(networkClient.getVehicles().results.map { it.toVehicleEntity() })
            lastUpdateRepository.storeLastUpdateCurrentTimeStamp(VEHICLES)
        }
    }
}