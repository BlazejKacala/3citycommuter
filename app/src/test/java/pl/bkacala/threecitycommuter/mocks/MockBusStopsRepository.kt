package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.tools.makeRandomInstance

object MockBusStopsRepository {
    val mockBusStopsRepository = object: BusStopsRepository {

        override fun getBusStops(): Flow<List<BusStopData>> {
            return flowOf(listOf(makeRandomInstance<BusStopData>()))
        }

        override fun getDepartures(stopId: Int): Flow<List<Departure>> {
            return flowOf(listOf(makeRandomInstance<Departure>()))

        }
    }
}