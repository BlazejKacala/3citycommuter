package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.rail.RailNetwork
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.rail.RailStationCatalog

internal class RailStaticCatalog(
    private val stationCatalog: RailStationCatalog,
) {
    private var stations = emptyList<TransitStopData>()

    suspend fun load() {
        stations = stationCatalog.getActiveStations().map { station ->
            TransitStopData(
                stopKey = TransitStopKey(TransitProvider.PLK, station.plkStationId),
                stopCode = station.plkStationId.toString(),
                stopName = station.name,
                stopShortName = station.name,
                stopDesc = station.name,
                subName = null,
                date = null,
                zoneId = 0,
                zoneName = station.network.name,
                virtual = 0,
                nonpassenger = 0,
                depot = 0,
                ticketZoneBorder = 0,
                onDemand = false,
                activationDate = null,
                stopLat = station.latitude,
                stopLon = station.longitude,
                stopUrl = station.network.url,
                locationType = "1",
                parentStation = null,
                stopTimezone = "Europe/Warsaw",
                wheelchairBoarding = "1",
                isForBuses = false,
                isForTrams = false,
                railNetwork = station.network,
            )
        }
    }

    val stops: List<TransitStopData> get() = stations

    val railNetworksById: Map<Int, RailNetwork>
        get() = stops.associate { it.sourceStopId to requireNotNull(it.railNetwork) }
    val stopNamesById: Map<Int, String>
        get() = stops.associate { it.sourceStopId to it.name }

    fun routeFor(stopIds: List<Int>): Route =
        Route(
            shape = stopIds.mapNotNull { stopId ->
                stops.firstOrNull { it.sourceStopId == stopId }?.let { Route.GeoPoint(it.stopLat, it.stopLon) }
            },
            stops = stopIds.mapIndexedNotNull { sequence, stopId ->
                stops.firstOrNull { it.sourceStopId == stopId }?.let { stop ->
                    Route.Stop(stop.stopKey, sequence)
                }
            },
        )
}

private val RailNetwork.url: String
    get() = when (this) {
        RailNetwork.SKM -> "https://www.skm.pkp.pl/"
        RailNetwork.PKM -> "https://www.pkm-sa.pl/"
    }
