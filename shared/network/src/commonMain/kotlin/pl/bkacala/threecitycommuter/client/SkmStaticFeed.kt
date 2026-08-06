package pl.bkacala.threecitycommuter.client

import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.rail.RailNetwork
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.resource.loadRailStationsSeed

internal class SkmStaticFeed(
    json: Json,
) {
    private val stations = loadRailStationsSeed(json)
        .filter { it.isActive }

    val stops: List<TransitStopData> = stations.map { station ->
        TransitStopData(
            stopKey = TransitStopKey(TransitProvider.SKM, station.plkStationId),
            stopCode = station.plkStationId.toString(),
            stopName = station.name,
            stopShortName = station.name,
            stopDesc = station.name,
            subName = null,
            date = null,
            zoneId = 0,
            zoneName = "SKM",
            virtual = 0,
            nonpassenger = 0,
            depot = 0,
            ticketZoneBorder = 0,
            onDemand = false,
            activationDate = null,
            stopLat = station.latitude,
            stopLon = station.longitude,
            stopUrl = "https://www.skm.pkp.pl/",
            locationType = "1",
            parentStation = null,
            stopTimezone = "Europe/Warsaw",
            wheelchairBoarding = "1",
            isForBuses = false,
            isForTrams = false,
            railNetwork = station.network,
        )
    }

    private val stopsById = stops.associateBy { it.sourceStopId }
    val railNetworksById: Map<Int, RailNetwork> = stops.associate { it.sourceStopId to requireNotNull(it.railNetwork) }
    val stopNamesById: Map<Int, String> = stops.associate { it.sourceStopId to it.name }

    fun routeFor(stopIds: List<Int>): Route =
        Route(
            shape = stopIds.mapNotNull { stopId ->
                stopsById[stopId]?.let { Route.GeoPoint(it.stopLat, it.stopLon) }
            },
        )
}
