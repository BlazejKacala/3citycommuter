package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.TransitStopsDao
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThan

private const val TRANSIT_STOPS_KEY = "transit_stops"
private const val TRANSIT_STOPS_CACHE_VERSION_KEY = "transit_stops_cache_version"
private const val TRANSIT_STOPS_CACHE_VERSION = 5L
private const val LOG_TAG = "RealTransitStopsRepository"

internal class RealTransitStopsRepository(
    private val transitDataSource: TransitDataSource,
    private val transitStopsDao: TransitStopsDao,
    private val lastUpdateRepository: LastUpdateRepository,
) : TransitStopsRepository {
    private val refreshMutex = Mutex()
    private val seedMutex = Mutex()

    override fun getTransitStops(): Flow<List<TransitStopData>> = flow {
        val localStops = seedMutex.withLock {
            val storedStops = transitStopsDao.getRealTransitStops()
            if (storedStops.isNotEmpty()) {
                storedStops.map { it.toStopData() }
            } else {
                val bundledStops = transitDataSource.getBundledStops()
                storeStops(bundledStops)
                transitStopsDao.getRealTransitStops().map { it.toStopData() }
            }
        }
        if (localStops.isNotEmpty()) emit(localStops)

        // Keep collecting after the local emission to receive the refreshed catalog.
        val refreshedStops = refreshMutex.withLock {
            val timestamp = lastUpdateRepository.getLastUpdateTimeStamp(TRANSIT_STOPS_KEY)
            val version = lastUpdateRepository.getLong(TRANSIT_STOPS_CACHE_VERSION_KEY, 0)
            if (localStops.isEmpty() || version < TRANSIT_STOPS_CACHE_VERSION ||
                timestamp.isOlderThan(StopCatalogCacheConfig.refreshInterval)
            ) {
                try {
                    val stops = transitDataSource.getStops()
                    check(stops.isNotEmpty()) { "Refreshed stop catalog is empty" }
                    storeStops(stops)
                    lastUpdateRepository.storeLastUpdateCurrentTimeStamp(TRANSIT_STOPS_KEY)
                    lastUpdateRepository.putLong(TRANSIT_STOPS_CACHE_VERSION_KEY, TRANSIT_STOPS_CACHE_VERSION)
                } catch (throwable: Exception) {
                    if (throwable is CancellationException || localStops.isEmpty()) throw throwable
                    logError(LOG_TAG, "Failed to refresh stop catalog; using cached data", throwable)
                    return@withLock null
                }
                transitStopsDao.getRealTransitStops().map { it.toStopData() }
            } else {
                null
            }
        }
        if (refreshedStops != null && refreshedStops != localStops) emit(refreshedStops)
    }.flowOn(Dispatchers.IO)

    private suspend fun storeStops(stops: List<TransitStopData>) {
        transitStopsDao.upsertTransitStops(stops.map { it.toEntity() })
    }

    override fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>> = flow {
        emit(transitDataSource.getDepartures(stopKey))
    }.flowOn(Dispatchers.IO)
}
