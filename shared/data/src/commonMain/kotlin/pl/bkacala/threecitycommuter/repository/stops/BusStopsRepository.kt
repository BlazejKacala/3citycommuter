package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

interface BusStopsRepository {
    fun getBusStops(): Flow<List<BusStopData>>

    fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>>

}
