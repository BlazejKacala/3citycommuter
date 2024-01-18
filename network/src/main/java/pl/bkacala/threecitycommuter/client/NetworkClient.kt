package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.StopsNetworkData

interface NetworkClient {
    suspend fun getStops() : StopsNetworkData
}