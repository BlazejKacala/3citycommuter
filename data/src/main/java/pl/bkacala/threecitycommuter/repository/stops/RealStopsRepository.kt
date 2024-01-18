package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.model.stops.StopData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import javax.inject.Inject

class RealStopsRepository @Inject constructor(
    private val networkClient: NetworkClient,
    private val busStopsDao: BusStopsDao
) : StopsRepository {
    override fun getBusStops(): Flow<List<StopData>> {
        return flow {
            withContext(Dispatchers.IO) {
                busStopsDao.upsertBusStations(networkClient.getStops().stops.map { it.toEntity() })
            }
            emit(busStopsDao.getBusStations().map { it.toStopData() })
        }
    }
}