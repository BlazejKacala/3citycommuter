package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pl.bkacala.threecitycommuter.model.BusStopsNetworkData
import pl.bkacala.threecitycommuter.model.DepartureNetworkData
import pl.bkacala.threecitycommuter.model.RouteNetworkData
import pl.bkacala.threecitycommuter.model.VehicleNetworkData
import pl.bkacala.threecitycommuter.model.VehiclePositionNetworkData
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopId
import pl.bkacala.threecitycommuter.model.transit.supportsLiveVehicleTracking
import pl.bkacala.threecitycommuter.model.transit.supportsRouteShapes
import pl.bkacala.threecitycommuter.model.transit.supportsVehicleMetadata
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.utils.toddMMyyyyString

internal class GdanskTransitDataSource(
    private val networkClient: NetworkClient,
) : TransitDataSource {

    override fun features(provider: TransitProvider): TransitFeatures =
        TransitFeatures(
            provider = TransitProvider.GDANSK,
            supportsLiveVehicleTracking = TransitProvider.GDANSK.supportsLiveVehicleTracking,
            supportsRouteShapes = TransitProvider.GDANSK.supportsRouteShapes,
            supportsVehicleMetadata = TransitProvider.GDANSK.supportsVehicleMetadata,
        )

    override suspend fun getStops(): List<BusStopData> =
        networkClient.getStops().stops.map { it.toBusStopData(isForBuses = true, isForTrams = true) }

    override suspend fun getDepartures(stopId: Int): List<Departure> =
        networkClient.getDepartures(stopId).departures.map { it.toDepartureData() }

    override suspend fun getRouteShape(provider: TransitProvider, routeId: Int, tripId: Int): Route {
        val dateString =
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toddMMyyyyString()
        return networkClient.getRoute(dateString, routeId, tripId).mapToRoute()
    }

    override suspend fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): VehiclePosition? =
        networkClient.getVehiclesPositions().vehiclePositions
            .firstOrNull { it.vehicleId == vehicleId }
            ?.toVehiclePosition()

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> =
        networkClient.getVehicles().results.map { it.toVehicle() }
}

private fun BusStopsNetworkData.BusStopNetworkData.toBusStopData(
    isForBuses: Boolean,
    isForTrams: Boolean,
): BusStopData {
    return BusStopData(
        stopId = TransitStopId.toAppId(TransitProvider.GDANSK, stopId),
        stopCode = stopCode,
        stopName = stopName,
        stopShortName = stopShortName,
        stopDesc = stopDesc,
        subName = subName,
        date = date,
        zoneId = zoneId ?: -1,
        zoneName = zoneName,
        virtual = virtual ?: -1,
        nonpassenger = nonpassenger ?: -1,
        depot = depot ?: -1,
        ticketZoneBorder = ticketZoneBorder ?: -1,
        onDemand = onDemand == 1,
        activationDate = activationDate,
        stopLat = stopLat,
        stopLon = stopLon,
        stopUrl = stopUrl,
        locationType = locationType,
        parentStation = parentStation,
        stopTimezone = stopTimezone,
        wheelchairBoarding = wheelchairBoarding,
        isForBuses = isForBuses,
        isForTrams = isForTrams,
    )
}

private fun DepartureNetworkData.toDepartureData(): Departure {
    return Departure(
        id = id,
        delayInSeconds = delayInSeconds,
        estimatedTime = estimatedTime,
        headsign = headsign,
        lineNumber = routeId.toString(),
        routeId = routeId,
        scheduledTripStartTime = scheduledTripStartTime,
        tripId = tripId,
        status = status,
        theoreticalTime = theoreticalTime,
        timestamp = timestamp,
        trip = trip,
        vehicleCode = vehicleCode,
        vehicleId = vehicleId,
        vehicleService = vehicleService,
    )
}

private fun RouteNetworkData.mapToRoute(): Route {
    return Route(
        shape = coordinates.mapNotNull { coordinate ->
            if (coordinate.size == 2) {
                Route.GeoPoint(latitude = coordinate[1], longitude = coordinate[0])
            } else {
                null
            }
        },
    )
}

private fun VehicleNetworkData.toVehicle(): Vehicle {
    return Vehicle(
        photo = photo,
        vehicleCode = vehicleCode,
        carrirer = carrirer,
        transportationType = transportationType,
        vehicleCharacteristics = vehicleCharacteristics,
        bidirectional = bidirectional,
        historicVehicle = historicVehicle,
        length = length,
        brand = brand,
        model = model,
        productionYear = productionYear,
        seats = seats,
        standingPlaces = standingPlaces,
        airConditioning = airConditioning,
        monitoring = monitoring,
        internalMonitor = internalMonitor,
        floorHeight = floorHeight,
        kneelingMechanism = kneelingMechanism,
        wheelchairsRamp = wheelchairsRamp,
        usb = usb,
        voiceAnnouncements = voiceAnnouncements,
        aed = aed,
        bikeHolders = bikeHolders,
        ticketMachine = ticketMachine,
        patron = patron,
        url = url,
        passengersDoors = passengersDoors,
    )
}

private fun VehiclePositionNetworkData.toVehiclePosition(): VehiclePosition {
    return VehiclePosition(
        generated = generated,
        routeShortName = routeShortName,
        tripId = tripId,
        headsign = headsign,
        vehicleCode = vehicleCode,
        vehicleService = vehicleService,
        vehicleId = vehicleId,
        speed = speed,
        direction = direction,
        delay = delay,
        scheduledTripStartTime = scheduledTripStartTime,
        lat = lat,
        lon = lon,
        gpsQuality = gpsQuality,
    )
}
