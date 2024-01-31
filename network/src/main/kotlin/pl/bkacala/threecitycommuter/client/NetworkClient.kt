package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.BusStopsNetworkData
import pl.bkacala.threecitycommuter.model.DepartureList

interface NetworkClient {
    suspend fun getStops(): BusStopsNetworkData

    suspend fun getDepartures(stopId: Int): DepartureList
}