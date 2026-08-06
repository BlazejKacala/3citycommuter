package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

interface TransitStopsRepository {
    fun getTransitStops(): Flow<List<TransitStopData>>

    fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>>

}
