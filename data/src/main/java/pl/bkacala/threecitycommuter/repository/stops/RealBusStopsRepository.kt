package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThenOneDay
import javax.inject.Inject

private const val BUS_STOPS_KEY = "bus_stops"

class RealBusStopsRepository @Inject constructor(
    private val networkClient: NetworkClient,
    private val busStopsDao: BusStopsDao,
    private val lastUpdateRepository: LastUpdateRepository
) : BusStopsRepository {

    override fun getBusStops(): Flow<List<BusStopData>> {
        return flow {
            val busStationsFromDb = busStopsDao.getBusStations()
            val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(BUS_STOPS_KEY)

            if (busStationsFromDb.isEmpty() || lastUpdateTimestamp.isOlderThenOneDay()) {
                busStopsDao.upsertBusStations(networkClient.getStops().stops.map { it.toEntity() })
                lastUpdateRepository.storeLastUpdateCurrentTimeStamp(BUS_STOPS_KEY)
            }
            emit(busStopsDao.getBusStations()
                .filter { it.virtual == 0 }
                .map { it.toStopData() })
        }.flowOn(Dispatchers.IO)
    }
}