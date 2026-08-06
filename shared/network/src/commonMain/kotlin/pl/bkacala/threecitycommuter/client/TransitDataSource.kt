package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition

interface TransitDataSource {
    fun features(provider: pl.bkacala.threecitycommuter.model.transit.TransitProvider): TransitFeatures

    suspend fun getStops(): List<BusStopData>

    suspend fun getBundledStops(): List<BusStopData> = emptyList()

    suspend fun getDepartures(stopKey: TransitStopKey): List<Departure>

    suspend fun getRouteShape(
        provider: pl.bkacala.threecitycommuter.model.transit.TransitProvider,
        routeId: Int,
        tripId: Int,
    ): Route?

    suspend fun getVehiclePosition(
        provider: pl.bkacala.threecitycommuter.model.transit.TransitProvider,
        vehicleId: Int,
    ): VehiclePosition?

    suspend fun getVehicles(provider: pl.bkacala.threecitycommuter.model.transit.TransitProvider): List<Vehicle>
}
