package pl.bkacala.threecitycommuter.client

import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.BusStopType
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.resource.readBundledResourceText

internal class CombinedTransitDataSource(
    private val gdanskDataSource: GdanskTransitDataSource,
    private val gdyniaDataSource: GdyniaTransitDataSource,
    private val skmDataSource: SkmTransitDataSource,
    private val json: Json,
) : TransitDataSource {
    private val stopTypesByKey: Map<TransitStopKey, BusStopType> by lazy {
        json.decodeFromString<List<BusStopType>>(readBundledResourceText("relations.json"))
            .associateBy { it.stopKey }
    }

    override fun features(provider: TransitProvider): TransitFeatures =
        dataSourceFor(provider).features(provider)

    override suspend fun getStops(): List<BusStopData> =
        enrichStopTypes(
            gdanskDataSource.getStops() + gdyniaDataSource.getStops() + skmDataSource.getStops(),
        )

    override suspend fun getBundledStops(): List<BusStopData> =
        enrichStopTypes(
            gdanskDataSource.getBundledStops() + gdyniaDataSource.getBundledStops() +
                skmDataSource.getBundledStops(),
        )

    private fun enrichStopTypes(stops: List<BusStopData>): List<BusStopData> =
        stops.map { stop ->
            stopTypesByKey[stop.stopKey]?.let { type ->
                stop.copy(
                    isForBuses = type.isForBuses,
                    isForTrams = type.isForTrams,
                )
            } ?: stop
        }

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
