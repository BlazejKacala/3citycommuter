package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.model.stops.StopData
import pl.bkacala.threecitycommuter.model.stops.toStopData
import javax.inject.Inject

class RealStopsRepository @Inject constructor(
    private val networkClient: NetworkClient
) : StopsRepository {
    override fun getBusStops(): Flow<List<StopData>> {
        return flow { emit(networkClient.getStops().stops.map { it.toStopData() }) }
    }
}