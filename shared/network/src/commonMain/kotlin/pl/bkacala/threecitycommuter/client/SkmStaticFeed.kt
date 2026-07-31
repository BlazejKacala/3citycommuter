package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

internal object SkmStaticFeed {
    val stops: List<BusStopData> = listOf(
        station(101, "Gdansk Srodmiescie", 54.34805, 18.64672),
        station(102, "Gdansk Glowny", 54.35634, 18.64421),
        station(103, "Gdansk Wrzeszcz", 54.38091, 18.60584),
        station(104, "Sopot", 54.44195, 18.56082),
        station(105, "Gdynia Glowna", 54.51897, 18.53054),
        station(106, "Wejherowo", 54.60537, 18.23559),
    )

    val routeShapesByTripId: Map<Int, Route> = mapOf(
        9101 to routeFor(101, 102, 103, 104, 105),
        9102 to routeFor(105, 104, 103, 102, 101),
        9103 to routeFor(103, 104, 105, 106),
        9104 to routeFor(106, 105, 104, 103),
    )

    fun stopBySourceId(stopId: Int): BusStopData? =
        stops.firstOrNull { it.sourceStopId == stopId }

    private fun routeFor(vararg stopIds: Int): Route =
        Route(
            shape = stopIds.toList().mapNotNull { stopId ->
                stopBySourceId(stopId)?.let { Route.GeoPoint(it.stopLat, it.stopLon) }
            },
        )

    private fun station(
        sourceStopId: Int,
        name: String,
        latitude: Double,
        longitude: Double,
    ): BusStopData =
        BusStopData(
            stopKey = TransitStopKey(TransitProvider.SKM, sourceStopId),
            stopCode = sourceStopId.toString(),
            stopName = name,
            stopShortName = name,
            stopDesc = name,
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
            stopLat = latitude,
            stopLon = longitude,
            stopUrl = "https://www.skm.pkp.pl/",
            locationType = "1",
            parentStation = null,
            stopTimezone = "Europe/Warsaw",
            wheelchairBoarding = "1",
            isForBuses = false,
            isForTrams = false,
        )
}
