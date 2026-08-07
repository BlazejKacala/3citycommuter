package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition

interface ProviderTransitDataSource {
    val provider: TransitProvider

    fun features(): TransitFeatures

    suspend fun getStops(): List<TransitStopData>

    suspend fun getBundledStops(): List<TransitStopData> = emptyList()

    suspend fun getDepartures(stopKey: TransitStopKey): List<Departure>

    suspend fun getRouteShape(routeId: Int, tripId: Int): Route?

    suspend fun getVehiclePosition(vehicleId: Int): VehiclePosition?

    suspend fun getVehicles(): List<Vehicle>
}
