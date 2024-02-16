package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.dao.BusStopsTypesDao
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.departures.toDepartureData
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.BusStopType
import pl.bkacala.threecitycommuter.model.stops.toData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThenOneDay
import javax.inject.Inject

private const val BUS_STOPS_KEY = "bus_stops"

internal class RealBusStopsRepository @Inject constructor(
    private val networkClient: NetworkClient,
    private val busStopsDao: BusStopsDao,
    private val busStopsTypesDao: BusStopsTypesDao,
    private val lastUpdateRepository: LastUpdateRepository,
) : BusStopsRepository {

    override fun getBusStops(): Flow<List<BusStopData>> {
        return flow {

            val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(BUS_STOPS_KEY)

            if (lastUpdateTimestamp.isOlderThenOneDay()) {
                busStopsDao.upsertBusStations(networkClient.getStops().stops.map { it.toEntity() })
                lastUpdateRepository.storeLastUpdateCurrentTimeStamp(BUS_STOPS_KEY)
            }
            val relations = busStopsTypesDao.getBusStopsTypes().map { it.toData() }
            emit(busStopsDao.getBusStations()
                .filter { it.virtual == 0 }
                .mapNotNull { entity ->
                    //TODO this should be done in more optimized way, using hashmap or directly through db relations
                    val relation = relations.firstOrNull { entity.stopId == it.stopId }
                    relation?.let {
                        entity.toStopData(it.isForBuses, it.isForTrams)
                    }
                })
        }.flowOn(Dispatchers.IO)
    }

    override fun getDepartures(stopId: Int): Flow<List<Departure>> {
        return flow {
            emit(networkClient.getDepartures(stopId).departures.map { it.toDepartureData() })
        }
    }

    override fun storeBusStopsTypes(types: List<BusStopType>) {
        busStopsTypesDao.upsertBusStopsTypes(types.map { it.toEntity() })
    }
}