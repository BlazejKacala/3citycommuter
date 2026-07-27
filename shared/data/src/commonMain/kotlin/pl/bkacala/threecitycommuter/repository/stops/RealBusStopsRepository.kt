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
import pl.bkacala.threecitycommuter.model.transit.TransitStopId
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThenOneDay

private const val BUS_STOPS_KEY = "bus_stops"

internal class RealBusStopsRepository(
    private val transitDataSource: TransitDataSource,
    private val busStopsDao: BusStopsDao,
    private val busStopsTypesDao: BusStopsTypesDao,
    private val lastUpdateRepository: LastUpdateRepository,
) : BusStopsRepository {

    override fun getBusStops(): Flow<List<BusStopData>> {
        return flow {
            val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(BUS_STOPS_KEY)

            if (lastUpdateTimestamp.isOlderThenOneDay()) {
                val stops = transitDataSource.getStops()
                busStopsDao.upsertBusStations(stops.map { it.toEntity() })
                busStopsTypesDao.upsertBusStopsTypes(
                    stops.filter { it.provider == TransitProvider.GDYNIA }.map { it.toType().toEntity() },
                )
                lastUpdateRepository.storeLastUpdateCurrentTimeStamp(BUS_STOPS_KEY)
            }
            val relationsByStopId = busStopsTypesDao.getBusStopsTypes()
                .associate { entity ->
                    entity.busStopId to entity.toData()
                }
            emit(
                busStopsDao.getRealBusStations()
                    .mapNotNull { entity ->
                        val relation = relationsByStopId[entity.stopId]
                        relation?.let {
                            entity.toStopData(it.isForBuses, it.isForTrams)
                        }
                    },
            )
        }.flowOn(Dispatchers.IO)
    }

    override fun getDepartures(stopId: Int): Flow<List<Departure>> {
        return flow {
            emit(transitDataSource.getDepartures(stopId))
        }
    }

    override suspend fun storeBusStopsTypes(types: List<BusStopType>) {
        busStopsTypesDao.upsertBusStopsTypes(
            types.map { type ->
                type.copy(stopId = TransitStopId.toAppId(TransitProvider.GDANSK, type.stopId)).toEntity()
            },
        )
    }
}
