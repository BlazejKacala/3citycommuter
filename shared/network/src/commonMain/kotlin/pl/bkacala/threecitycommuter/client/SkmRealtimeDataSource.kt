package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.departures.Departure

internal interface SkmRealtimeDataSource {
    suspend fun getDepartures(stopId: Int): List<Departure>
}
