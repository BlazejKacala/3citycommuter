package pl.bkacala.threecitycommuter.client

import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.stops.TransitStopType
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.resource.readBundledResourceText

internal class CombinedTransitDataSource(
    private val gdanskDataSource: GdanskTransitDataSource,
    private val gdyniaDataSource: GdyniaTransitDataSource,
    private val railDataSource: RailTransitDataSource,
    private val json: Json,
) : TransitDataSource {
    private val stopTypesByKey: Map<TransitStopKey, TransitStopType> by lazy {
        json.decodeFromString<List<TransitStopType>>(readBundledResourceText("relations.json"))
            .associateBy { it.stopKey }
    }

    override fun features(provider: TransitProvider): TransitFeatures =
        dataSourceFor(provider).features()

    override suspend fun getStops(): List<TransitStopData> =
        enrichStopTypes(
            gdanskDataSource.getStops() + gdyniaDataSource.getStops() + railDataSource.getStops(),
        )

    override suspend fun getBundledStops(): List<TransitStopData> =
        enrichStopTypes(
            gdanskDataSource.getBundledStops() + gdyniaDataSource.getBundledStops() +
                railDataSource.getBundledStops(),
        )

    private fun enrichStopTypes(stops: List<TransitStopData>): List<TransitStopData> =
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
    ): Route? = dataSourceFor(provider).getRouteShape(routeId, tripId)

    override suspend fun getVehiclePosition(
        provider: TransitProvider,
        vehicleId: Int,
    ): VehiclePosition? = dataSourceFor(provider).getVehiclePosition(vehicleId)

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> =
        dataSourceFor(provider).getVehicles()

    private fun dataSourceFor(provider: TransitProvider): ProviderTransitDataSource {
        return when (provider) {
            TransitProvider.GDANSK -> gdanskDataSource
            TransitProvider.GDYNIA -> gdyniaDataSource
            TransitProvider.PLK -> railDataSource
        }
    }
}
