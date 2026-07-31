package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition

internal class CombinedTransitDataSource(
    private val gdanskDataSource: GdanskTransitDataSource,
    private val gdyniaDataSource: GdyniaTransitDataSource,
    private val skmDataSource: SkmTransitDataSource,
) : TransitDataSource {

    override fun features(provider: TransitProvider): TransitFeatures =
        dataSourceFor(provider).features(provider)

    override suspend fun getStops(): List<BusStopData> =
        gdanskDataSource.getStops() + gdyniaDataSource.getStops() + skmDataSource.getStops()

    override suspend fun getDepartures(stopKey: TransitStopKey): List<Departure> =
        dataSourceFor(stopKey.provider).getDepartures(stopKey)

    override suspend fun getRouteShape(
        provider: TransitProvider,
        routeId: Int,
        tripId: Int,
    ): Route? = dataSourceFor(provider).getRouteShape(provider, routeId, tripId)

    override suspend fun getVehiclePosition(
        provider: TransitProvider,
        vehicleId: Int,
    ): VehiclePosition? = dataSourceFor(provider).getVehiclePosition(provider, vehicleId)

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> =
        dataSourceFor(provider).getVehicles(provider)

    private fun dataSourceFor(provider: TransitProvider): TransitDataSource {
        return when (provider) {
            TransitProvider.GDANSK -> gdanskDataSource
            TransitProvider.GDYNIA -> gdyniaDataSource
            TransitProvider.SKM -> skmDataSource
        }
    }
}
