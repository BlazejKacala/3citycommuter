package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.TransitStopsDao
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.logging.logError
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

    override fun getTransitStops(): Flow<List<TransitStopData>> {
        return flow {
            val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(TRANSIT_STOPS_KEY)
            val cachedVersion = lastUpdateRepository.getLong(TRANSIT_STOPS_CACHE_VERSION_KEY, 0)
            val hasStoredStops = transitStopsDao.getRealTransitStops().isNotEmpty()
            val cacheNeedsSeeding = !hasStoredStops || cachedVersion < TRANSIT_STOPS_CACHE_VERSION

            if (cacheNeedsSeeding) {
                val bundledStops = transitDataSource.getBundledStops()
                if (bundledStops.isNotEmpty()) {
                    storeStops(bundledStops)
                }
            }

            if (cacheNeedsSeeding || lastUpdateTimestamp.isOlderThan(StopCatalogCacheConfig.refreshInterval)) {
                try {
                    storeStops(transitDataSource.getStops())
                    lastUpdateRepository.storeLastUpdateCurrentTimeStamp(TRANSIT_STOPS_KEY)
                } catch (throwable: Throwable) {
                    if (transitStopsDao.getRealTransitStops().isEmpty()) {
                        throw throwable
                    }
                    logError(LOG_TAG, "Failed to refresh stop catalog; using cached data", throwable)
                }
                lastUpdateRepository.putLong(TRANSIT_STOPS_CACHE_VERSION_KEY, TRANSIT_STOPS_CACHE_VERSION)
            }
            val storedStops = transitStopsDao.getRealTransitStops()
            emit(storedStops.map { it.toStopData() })
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun storeStops(stops: List<TransitStopData>) {
        transitStopsDao.upsertTransitStops(stops.map { it.toEntity() })
    }

    override fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>> {
        return flow {
            emit(transitDataSource.getDepartures(stopKey))
        }.flowOn(Dispatchers.IO)
    }

}
