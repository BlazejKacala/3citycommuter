package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.BusStopsNetworkData

interface NetworkClient {
    suspend fun getStops() : BusStopsNetworkData
}