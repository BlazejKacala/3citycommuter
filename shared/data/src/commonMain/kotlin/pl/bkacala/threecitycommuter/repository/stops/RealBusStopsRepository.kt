package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.utils.isOlderThan

private const val BUS_STOPS_KEY = "bus_stops"
private const val BUS_STOPS_CACHE_VERSION_KEY = "bus_stops_cache_version"
private const val BUS_STOPS_CACHE_VERSION = 5L
private const val LOG_TAG = "RealBusStopsRepository"

internal class RealBusStopsRepository(
    private val transitDataSource: TransitDataSource,
    private val busStopsDao: BusStopsDao,
    private val lastUpdateRepository: LastUpdateRepository,
) : BusStopsRepository {

    override fun getBusStops(): Flow<List<BusStopData>> {
        return flow {
            val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(BUS_STOPS_KEY)
            val cachedVersion = lastUpdateRepository.getLong(BUS_STOPS_CACHE_VERSION_KEY, 0)
            val hasStoredStops = busStopsDao.getRealBusStations().isNotEmpty()
            val cacheNeedsSeeding = !hasStoredStops || cachedVersion < BUS_STOPS_CACHE_VERSION

            if (cacheNeedsSeeding) {
                val bundledStops = transitDataSource.getBundledStops()
                if (bundledStops.isNotEmpty()) {
                    storeStops(bundledStops)
                }
            }

            if (cacheNeedsSeeding || lastUpdateTimestamp.isOlderThan(StopCatalogCacheConfig.refreshInterval)) {
                try {
                    storeStops(transitDataSource.getStops())
                    lastUpdateRepository.storeLastUpdateCurrentTimeStamp(BUS_STOPS_KEY)
                } catch (throwable: Throwable) {
                    if (busStopsDao.getRealBusStations().isEmpty()) {
                        throw throwable
                    }
                    logError(LOG_TAG, "Failed to refresh stop catalog; using cached data", throwable)
                }
                lastUpdateRepository.putLong(BUS_STOPS_CACHE_VERSION_KEY, BUS_STOPS_CACHE_VERSION)
            }
            val storedStops = busStopsDao.getRealBusStations()
            emit(storedStops.map { it.toStopData() })
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun storeStops(stops: List<BusStopData>) {
        busStopsDao.upsertBusStations(stops.map { it.toEntity() })
    }

    override fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>> {
        return flow {
            emit(transitDataSource.getDepartures(stopKey))
        }.flowOn(Dispatchers.IO)
    }

}
