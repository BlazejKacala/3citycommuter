package pl.bkacala.threecitycommuter.repository.stops

import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.model.stops.StopData
import pl.bkacala.threecitycommuter.model.stops.toStopData
import javax.inject.Inject

class RealStopsRepository @Inject constructor(
    private val networkClient: NetworkClient
) : StopsRepository {
    override suspend fun getBusStops(): List<StopData> {
        return networkClient.getStops().stops.map { it.toStopData() }
    }
}