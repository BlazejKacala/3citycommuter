package pl.bkacala.threecitycommuter.repository.stops

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.departures.toDepartureData
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.BusStopRelation
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.stops.toStopData
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import pl.bkacala.threecitycommuter.utils.isOlderThenOneDay
import javax.inject.Inject

private const val BUS_STOPS_KEY = "bus_stops"

class RealBusStopsRepository @Inject constructor(
    private val networkClient: NetworkClient,
    private val busStopsDao: BusStopsDao,
    private val lastUpdateRepository: LastUpdateRepository,
    @ApplicationContext private val applicationContext: Context
) : BusStopsRepository {

    override fun getBusStops(): Flow<List<BusStopData>> {
        return flow {
            //TODO this should loaded from database and put there during splash
            val jsonString = applicationContext.assets.open("relations.json").bufferedReader().use {
                it.readText()
            }
            val relations: List<BusStopRelation> = Json.decodeFromString(jsonString)

            val lastUpdateTimestamp = lastUpdateRepository.getLastUpdateTimeStamp(BUS_STOPS_KEY)

            if (lastUpdateTimestamp.isOlderThenOneDay()) {
                busStopsDao.upsertBusStations(networkClient.getStops().stops.map { it.toEntity() })
                lastUpdateRepository.storeLastUpdateCurrentTimeStamp(BUS_STOPS_KEY)
            }
            emit(busStopsDao.getBusStations()
                .filter { it.virtual == 0 }
                .map { entity ->
                    //TODO this should be done in more optimized way, using hashmap or directly through db relations
                    val relation = relations.first { entity.stopId == it.stopId }
                    entity.toStopData(relation.isForBuses, relation.isForTrams)
                })
        }.flowOn(Dispatchers.IO)
    }

    override fun getDepartures(stopId: Int): Flow<List<Departure>> {
        return flow {
            emit(networkClient.getDepartures(stopId).departures.map { it.toDepartureData() })
        }
    }
}