package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.BusStopsNetworkData
import pl.bkacala.threecitycommuter.model.DepartureList
import pl.bkacala.threecitycommuter.model.VehiclePositionsNetworkData
import pl.bkacala.threecitycommuter.model.VehiclesNetworkData

interface NetworkClient {
    suspend fun getStops(): BusStopsNetworkData
    suspend fun getDepartures(stopId: Int): DepartureList
    suspend fun getVehicles(): VehiclesNetworkData
    suspend fun getVehiclesPositions(): VehiclePositionsNetworkData
}