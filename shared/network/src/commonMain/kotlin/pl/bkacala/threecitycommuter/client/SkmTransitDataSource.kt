package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.transit.supportsLiveVehicleTracking
import pl.bkacala.threecitycommuter.model.transit.supportsRouteShapes
import pl.bkacala.threecitycommuter.model.transit.supportsVehicleMetadata
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition

internal class SkmTransitDataSource(
    private val realtimeDataSource: SkmRealtimeDataSource,
) : TransitDataSource {

    override fun features(provider: TransitProvider): TransitFeatures =
        TransitFeatures(
            provider = TransitProvider.SKM,
            supportsLiveVehicleTracking = TransitProvider.SKM.supportsLiveVehicleTracking,
            supportsRouteShapes = TransitProvider.SKM.supportsRouteShapes,
            supportsVehicleMetadata = TransitProvider.SKM.supportsVehicleMetadata,
        )

    override suspend fun getStops(): List<BusStopData> = SkmStaticFeed.stops

    override suspend fun getDepartures(stopKey: TransitStopKey): List<Departure> =
        realtimeDataSource.getDepartures(stopKey)

    override suspend fun getRouteShape(provider: TransitProvider, routeId: Int, tripId: Int): Route? =
        SkmStaticFeed.routeShapesByTripId[tripId]

    override suspend fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): VehiclePosition? = null

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> = emptyList()
}
