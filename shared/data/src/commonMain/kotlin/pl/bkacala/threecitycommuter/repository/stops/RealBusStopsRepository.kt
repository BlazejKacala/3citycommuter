package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.dao.BusStopsTypesDao
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.BusStopType
import pl.bkacala.threecitycommuter.model.stops.toData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import pl.bkacala.threecitycommuter.model.stops.toType
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThenOneDay

private const val BUS_STOPS_KEY = "bus_stops"
private const val BUS_STOPS_CACHE_VERSION_KEY = "bus_stops_cache_version"
private const val BUS_STOPS_CACHE_VERSION = 3L

internal class RealBusStopsRepository(
    private val transitDataSource: TransitDataSource,
    private val busStopsDao: BusStopsDao,
    private val busStopsTypesDao: BusStopsTypesDao,
    private val lastUpdateRepository: LastUpdateRepository,
) : BusStopsRepository {

    override fun getBusStops(): Flow<List<BusStopData>> {
        return flow {
            val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(BUS_STOPS_KEY)
            val cachedVersion = lastUpdateRepository.getLong(BUS_STOPS_CACHE_VERSION_KEY, 0)

            if (lastUpdateTimestamp.isOlderThenOneDay() || cachedVersion < BUS_STOPS_CACHE_VERSION) {
                val stops = transitDataSource.getStops()
                busStopsDao.upsertBusStations(stops.map { it.toEntity() })
                busStopsTypesDao.upsertBusStopsTypes(
                    stops.filter { it.provider == TransitProvider.GDYNIA }.map { it.toType().toEntity() },
                )
                lastUpdateRepository.storeLastUpdateCurrentTimeStamp(BUS_STOPS_KEY)
                lastUpdateRepository.putLong(BUS_STOPS_CACHE_VERSION_KEY, BUS_STOPS_CACHE_VERSION)
            }
            val relationsByStopId = busStopsTypesDao.getBusStopsTypes()
                .map { it.toData() }
                .associateBy { it.stopKey }
            emit(
                busStopsDao.getRealBusStations()
                    .map { entity ->
                        val stopData = entity.toStopData(isForBuses = false, isForTrams = false)
                        val relation = relationsByStopId[stopData.stopKey]
                        if (relation != null) {
                            entity.toStopData(relation.isForBuses, relation.isForTrams)
                        } else if (TransitProvider.valueOf(entity.provider) == TransitProvider.GDANSK) {
                            entity.toStopData(isForBuses = true, isForTrams = false)
                        } else {
                            entity.toStopData(isForBuses = false, isForTrams = false)
                        }
                    },
            )
        }.flowOn(Dispatchers.IO)
    }

    override fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>> {
        return flow {
            emit(transitDataSource.getDepartures(stopKey))
        }
    }

    override suspend fun storeBusStopsTypes(types: List<BusStopType>) {
        busStopsTypesDao.upsertBusStopsTypes(types.map { it.toEntity() })
    }
}
