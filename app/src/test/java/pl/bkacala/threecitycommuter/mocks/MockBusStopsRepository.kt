package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.BusStopType
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.tools.makeRandomInstance

object MockBusStopsRepository {
    val mockBusStopsRepository = object: BusStopsRepository {

        override fun getBusStops(): Flow<List<BusStopData>> = flow {
            delay(100)
            emit(
                listOf(
                    makeRandomInstance<BusStopData>().copy(
                        stopLat = UserLocation.default().latitude,
                        stopLon = UserLocation.default().longitude
                    )
                )
            )
        }


        override fun getDepartures(stopId: Int): Flow<List<Departure>> = flow {
            val departure = makeRandomInstance<Departure>()
            delay(100)
            emit(listOf(makeRandomInstance<Departure>()))
        }

        override fun storeBusStopsTypes(types: List<BusStopType>) {
        }
    }
}