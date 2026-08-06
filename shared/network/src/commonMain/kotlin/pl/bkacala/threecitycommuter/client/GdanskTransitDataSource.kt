package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.gdansk.GdanskDepartureResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteShapeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclePositionResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehicleResponse
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitFeatures
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.transit.supportsLiveVehicleTracking
import pl.bkacala.threecitycommuter.model.transit.supportsRouteShapes
import pl.bkacala.threecitycommuter.model.transit.supportsVehicleMetadata
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.resource.readBundledResourceText
import pl.bkacala.threecitycommuter.utils.toddMMyyyyString

internal class GdanskTransitDataSource(
    private val gdanskApiClient: GdanskApiClient,
    private val json: Json,
) : TransitDataSource {

    override fun features(provider: TransitProvider): TransitFeatures =
        TransitFeatures(
            provider = TransitProvider.GDANSK,
            supportsLiveVehicleTracking = TransitProvider.GDANSK.supportsLiveVehicleTracking,
            supportsRouteShapes = TransitProvider.GDANSK.supportsRouteShapes,
            supportsVehicleMetadata = TransitProvider.GDANSK.supportsVehicleMetadata,
        )

    override suspend fun getStops(): List<TransitStopData> =
        gdanskApiClient.getStops().stops.map { it.toTransitStopData(isForBuses = true, isForTrams = true) }

    override suspend fun getBundledStops(): List<TransitStopData> {
        val payload = json.decodeFromString<Map<String, GdanskStopsResponse>>(
            readBundledResourceText("gdansk_stops.json"),
        )
        return payload.values
            .maxByOrNull { it.lastUpdate }
            ?.stops
            .orEmpty()
            .map { it.toTransitStopData(isForBuses = true, isForTrams = true) }
    }

    override suspend fun getDepartures(stopKey: TransitStopKey): List<Departure> =
        gdanskApiClient.getDepartures(stopKey.sourceStopId).departures.map { it.toDepartureData() }

    override suspend fun getRouteShape(provider: TransitProvider, routeId: Int, tripId: Int): Route {
        val dateString =
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toddMMyyyyString()
        val shape = gdanskApiClient.getRouteShape(dateString, routeId, tripId)
        val stopTimes = gdanskApiClient.getRouteStopTimes(dateString, routeId)

        return shape.mapToRoute(
            stops = stopTimes.stopTimes
                .asSequence()
                .filter { it.tripId == tripId }
                .filterNot { it.isNonPassengerStop() }
                .sortedBy { it.stopSequence }
                .map { stopTime ->
                    Route.Stop(
                        key = TransitStopKey(TransitProvider.GDANSK, stopTime.stopId),
                        sequence = stopTime.stopSequence,
                    )
                }
                .toList(),
        )
    }

    override suspend fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): VehiclePosition? =
        gdanskApiClient.getVehiclePositions().vehiclePositions
            .firstOrNull { it.vehicleId == vehicleId }
            ?.toVehiclePosition()

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> =
        gdanskApiClient.getVehicles().results.map { it.toVehicle() }
}

private fun GdanskStopResponse.toTransitStopData(
    isForBuses: Boolean,
    isForTrams: Boolean,
): TransitStopData {
    return TransitStopData(
        stopKey = TransitStopKey(TransitProvider.GDANSK, stopId),
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

private fun GdanskDepartureResponse.toDepartureData(): Departure {
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

private fun GdanskRouteShapeResponse.mapToRoute(stops: List<Route.Stop> = emptyList()): Route {
    return Route(
        shape = coordinates.mapNotNull { coordinate ->
            if (coordinate.size == 2) {
                Route.GeoPoint(latitude = coordinate[1], longitude = coordinate[0])
            } else {
                null
            }
        },
        stops = stops,
    )
}

private fun GdanskRouteStopTimeResponse.isNonPassengerStop(): Boolean =
    passenger == false || nonpassenger == 1 || virtual == 1

private fun GdanskVehicleResponse.toVehicle(): Vehicle {
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

private fun GdanskVehiclePositionResponse.toVehiclePosition(): VehiclePosition {
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
