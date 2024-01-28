package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.BusStopData

interface BusStopsRepository {
    fun getBusStops(): Flow<List<BusStopData>>

    fun getDepartures(stopId: Int): Flow<List<Departure>>
}