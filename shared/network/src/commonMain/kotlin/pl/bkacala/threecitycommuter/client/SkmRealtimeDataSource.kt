package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

internal interface SkmRealtimeDataSource {
    suspend fun getDepartures(stopKey: TransitStopKey): List<Departure>
}
