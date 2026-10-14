package pl.bkacala.threecitycommuter.repository.vehicles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.VehiclesDao
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.model.vehicles.toVehicle
import pl.bkacala.threecitycommuter.model.vehicles.toVehicleEntity
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThenOneDay
import kotlin.time.Duration.Companion.minutes

private const val VEHICLES = "vehicles_key"
private const val LOG_TAG = "RealVehiclesRepository"
private val REFRESH_RETRY_INTERVAL = 5.minutes

internal class RealVehiclesRepository(
    private val vehiclesDao: VehiclesDao,
    private val transitDataSource: TransitDataSource,
    private val lastUpdateRepository: LastUpdateRepository,
) : VehiclesRepository {
    private val catalogMutex = Mutex()
    private var catalog: VehicleCatalog? = null
    private var retryAfter: Instant? = null

    override suspend fun updateVehiclesBase() = withContext(Dispatchers.IO) {
        catalogMutex.withLock { refreshCatalog() }
    }

    override fun getVehicle(id: Int): Flow<Vehicle> = flow {
        emit(getCatalog().byCode.getValue(id))
    }.flowOn(Dispatchers.IO)

    override fun getVehicles(provider: TransitProvider): Flow<List<Vehicle>> = flow {
        emit(if (provider == TransitProvider.GDANSK) getCatalog().vehicles else emptyList())
    }.flowOn(Dispatchers.IO)

    override fun getVehiclesByCode(provider: TransitProvider): Flow<Map<Int, Vehicle>> = flow {
        emit(if (provider == TransitProvider.GDANSK) getCatalog().byCode else emptyMap())
    }.flowOn(Dispatchers.IO)

    override fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): Flow<VehiclePosition?> = flow {
        emit(transitDataSource.getVehiclePosition(provider, vehicleId))
    }.flowOn(Dispatchers.IO)

    private suspend fun getCatalog(): VehicleCatalog = catalogMutex.withLock {
        val current = catalog ?: VehicleCatalog(vehiclesDao.getVehicles().map { it.toVehicle() }).also { catalog = it }
        val stale = lastUpdateRepository.getLastUpdateTimeStamp(VEHICLES).isOlderThenOneDay()
        if ((stale || current.vehicles.isEmpty()) && retryAfter?.let { Clock.System.now() < it } != true) {
            try {
                refreshCatalog()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                retryAfter = Clock.System.now() + REFRESH_RETRY_INTERVAL
                logError(LOG_TAG, "Failed to refresh vehicle metadata; using cached data", exception)
            }
        }
        requireNotNull(catalog)
    }

    private suspend fun refreshCatalog() {
        val vehicles = transitDataSource.getVehicles(TransitProvider.GDANSK)
        check(vehicles.isNotEmpty()) { "Vehicle metadata response is empty" }
        vehiclesDao.upsertVehicles(vehicles.map { it.toVehicleEntity() })
        lastUpdateRepository.storeLastUpdateCurrentTimeStamp(VEHICLES)
        catalog = VehicleCatalog(vehicles)
        retryAfter = null
    }

    private class VehicleCatalog(val vehicles: List<Vehicle>) {
        val byCode = vehicles.mapNotNull { vehicle -> vehicle.vehicleCode.toIntOrNull()?.let { it to vehicle } }.toMap()
    }
}
