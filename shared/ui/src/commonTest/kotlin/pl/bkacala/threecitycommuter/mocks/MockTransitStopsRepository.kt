package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.stops.TransitStopsRepository
import pl.bkacala.threecitycommuter.tools.makeRandomInstance

object MockTransitStopsRepository {
    val mockTransitStopsRepository = object : TransitStopsRepository {

        override fun getTransitStops(): Flow<List<TransitStopData>> = flow {
            delay(100)
            emit(
                listOf(
                    makeRandomInstance<TransitStopData>().copy(
                        stopLat = UserLocation.default().latitude,
                        stopLon = UserLocation.default().longitude,
                    ),
                ),
            )
        }

        override fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>> = flow {
            delay(100)
            emit(listOf(makeRandomInstance<Departure>()))
        }
    }
}
