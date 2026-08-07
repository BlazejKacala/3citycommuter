package pl.bkacala.threecitycommuter.client

import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.minutes
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.plk.PlkOperationStationDto
import pl.bkacala.threecitycommuter.model.plk.PlkRouteDto
import pl.bkacala.threecitycommuter.model.plk.PlkStationOnRouteDto
import pl.bkacala.threecitycommuter.model.plk.PlkTrainOperationDto
import pl.bkacala.threecitycommuter.model.rail.RailNetwork
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
import pl.bkacala.threecitycommuter.logging.logError

internal class SkmTransitDataSource(
    private val plkApiClient: PlkApiClient,
    private val skmStaticFeed: SkmStaticFeed,
) : TransitDataSource {
    private val stationNamesMutex = Mutex()
    private var stationNamesById: Map<Int, String>? = null

    override fun features(provider: TransitProvider): TransitFeatures =
        TransitFeatures(
            provider = TransitProvider.SKM,
            supportsLiveVehicleTracking = TransitProvider.SKM.supportsLiveVehicleTracking,
            supportsRouteShapes = TransitProvider.SKM.supportsRouteShapes,
            supportsVehicleMetadata = TransitProvider.SKM.supportsVehicleMetadata,
        )

    override suspend fun getStops(): List<TransitStopData> = skmStaticFeed.stops

    override suspend fun getDepartures(stopKey: TransitStopKey): List<Departure> {
        val today = Clock.System.now().toLocalDateInSystemZone()
        val now = Clock.System.now()
        val stationId = stopKey.sourceStopId.toString()
        val carrierCode = carrierCodeFor(stopKey.sourceStopId)
        val lineLabel = lineLabelFor(stopKey.sourceStopId)
        val (schedules, operations) = supervisorScope {
            val schedulesDeferred = async {
                plkApiClient.getSchedules(
                    dateFrom = today.toString(),
                    dateTo = today.toString(),
                    stations = stationId,
                    carriersInclude = carrierCode,
                    fullRoutes = true,
                )
            }
            val operationsDeferred = async {
                plkApiClient.getOperations(
                    stations = stationId,
                    carriersInclude = carrierCode,
                    fullRoutes = true,
                )
            }
            val operations = try {
                operationsDeferred.await()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                logError(LOG_TAG, "Failed to load PLK operations; using scheduled departures", throwable)
                null
            }
            schedulesDeferred.await() to operations
        }
        val operationsByKey = operations?.trains.orEmpty().associateBy(::routeKey)
        val stationNames = scheduleStationNamesById(schedules)

        val scheduledDepartures = schedules.routes
            .asSequence()
            .filter { route -> route.carrierCode.equals(carrierCode, ignoreCase = true) }
            .mapNotNull { route ->
                val routeStation = route.stations.firstOrNull { it.stationId == stopKey.sourceStopId } ?: return@mapNotNull null
                val operation = operationsByKey[routeKey(route)]
                route.toDeparture(
                    routeStation = routeStation,
                    operation = operation,
                    operationStation = operation?.stations?.firstOrNull { it.stationId == stopKey.sourceStopId },
                    stationNames = stationNames,
                    operatingDate = operation?.operatingDate ?: today.toString(),
                    lineLabel = lineLabel,
                )
            }
            .filter { departure ->
                val departureInstant = departure.estimatedTime ?: departure.theoreticalTime
                departureInstant != null && departureInstant >= now.minus(2.minutes)
            }
            .sortedBy { it.estimatedTime ?: it.theoreticalTime }
            .toList()

        if (scheduledDepartures.isNotEmpty()) {
            return scheduledDepartures
        }

        return operations?.trains
            .orEmpty()
            .asSequence()
            .mapNotNull { train ->
                val station = train.stations.firstOrNull { it.stationId == stopKey.sourceStopId }
                    ?: return@mapNotNull null
                train.toDeparture(
                    station = station,
                    stationNames = stationNames,
                    lineLabel = lineLabel,
                )
            }
            .filter { departure ->
                val departureInstant = departure.estimatedTime ?: departure.theoreticalTime
                departureInstant != null && departureInstant >= now.minus(2.minutes)
            }
            .sortedBy { it.estimatedTime ?: it.theoreticalTime }
            .toList()
    }

    private fun carrierCodeFor(stationId: Int): String =
        when (skmStaticFeed.railNetworksById[stationId]) {
            RailNetwork.PKM -> PlkApiConfig.pkmCarrierCode
            RailNetwork.SKM, null -> PlkApiConfig.skmTricityCarrierCode
        }

    private fun lineLabelFor(stationId: Int): String =
        when (skmStaticFeed.railNetworksById[stationId]) {
            RailNetwork.PKM -> "PKM"
            RailNetwork.SKM, null -> "SKM"
        }

    override suspend fun getRouteShape(provider: TransitProvider, routeId: Int, tripId: Int): Route {
        val route = plkApiClient.getRoute(scheduleId = routeId, orderId = tripId)
        val orderedStopIds = route.stations
            .sortedBy { it.orderNumber }
            .map { it.stationId }
        return skmStaticFeed.routeFor(orderedStopIds)
    }

    override suspend fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): VehiclePosition? = null

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> = emptyList()

    private suspend fun scheduleStationNamesById(
        schedules: pl.bkacala.threecitycommuter.model.plk.PlkScheduleResponse,
    ): Map<Int, String> =
        stationNamesMutex.withLock {
            stationNamesById?.let { cached ->
                if (cached.isNotEmpty()) {
                    return cached
                }
            }

            val resolved = schedules.dictionaries?.stations
                ?.mapNotNull { (key, value) ->
                    val stationId = key.toIntOrNull() ?: return@mapNotNull null
                    val stationName = value.name ?: return@mapNotNull null
                    stationId to stationName
                }
                ?.toMap()
                .orEmpty()
            val merged = skmStaticFeed.stopNamesById + resolved
            stationNamesById = merged
            merged
        }

    private fun routeKey(route: PlkRouteDto): String = routeKey(route.scheduleId, route.orderId)

    private fun routeKey(route: PlkTrainOperationDto): String = routeKey(route.scheduleId, route.orderId)

    private fun routeKey(
        scheduleId: Int,
        orderId: Int,
    ): String = "$scheduleId|$orderId"

    private fun PlkRouteDto.toDeparture(
        routeStation: PlkStationOnRouteDto,
        operation: PlkTrainOperationDto?,
        operationStation: PlkOperationStationDto?,
        stationNames: Map<Int, String>,
        operatingDate: String,
        lineLabel: String,
    ): Departure {
        val firstStation = stations.minByOrNull { it.orderNumber }
        val destinationStationId = resolveDestinationStationId(
            routeStation = routeStation,
            operation = operation,
        )
        val theoreticalTime = operationStation?.plannedDeparture?.toPlkOperationInstant()
            ?: operationStation?.plannedArrival?.toPlkOperationInstant()
            ?: routeStation.departureTime?.toPlkRouteInstant(operatingDate, routeStation.departureDay ?: 0)
            ?: routeStation.arrivalTime?.toPlkRouteInstant(operatingDate, routeStation.arrivalDay ?: 0)
        val estimatedTime = operationStation?.actualDeparture?.toPlkOperationInstant()
            ?: operationStation?.actualArrival?.toPlkOperationInstant()
            ?: theoreticalTime

        return Departure(
            id = listOf(scheduleId, orderId, trainOrderId ?: orderId, operatingDate, routeStation.stationId).joinToString("-"),
            delayInSeconds = operationStation?.departureDelayMinutes?.times(60),
            estimatedTime = estimatedTime,
            headsign = destinationStationId?.let(stationNames::get) ?: name,
            lineNumber = lineLabel,
            routeId = scheduleId,
            scheduledTripStartTime = firstStation?.departureTime?.toPlkRouteInstant(operatingDate, firstStation.departureDay ?: 0),
            tripId = orderId,
            status = when {
                operationStation?.isCancelled == true -> "X"
                else -> null
            },
            theoreticalTime = theoreticalTime,
            timestamp = Clock.System.now(),
            trip = (trainOrderId ?: orderId).toLong(),
            vehicleCode = null,
            vehicleId = null,
            vehicleService = commercialCategorySymbol,
        )
    }

    private fun PlkRouteDto.resolveDestinationStationId(
        routeStation: PlkStationOnRouteDto,
        operation: PlkTrainOperationDto?,
    ): Int? {
        val currentOperationStation = operation?.stations?.firstOrNull { it.stationId == routeStation.stationId }
        val destinationFromOperation = currentOperationStation
            ?.let { current ->
                operation.stations.destinationOperationStationId(current.sequenceNumber())
            }
        if (destinationFromOperation != null) {
            return destinationFromOperation
        }

        val destinationFromRoute = stations.destinationRouteStationId(routeStation.orderNumber)
        if (destinationFromRoute != null) {
            return destinationFromRoute
        }

        return stations
            .filter { it.stationId != routeStation.stationId }
            .minByOrNull { it.orderNumber }
            ?.stationId
    }

    private fun PlkOperationStationDto.sequenceNumber(): Int = plannedSequenceNumber ?: actualSequenceNumber

    private fun List<PlkOperationStationDto>.destinationOperationStationId(currentSequenceNumber: Int): Int? {
        val stationsAfterCurrent = filter { it.sequenceNumber() > currentSequenceNumber }
        return stationsAfterCurrent.maxByOrNull { it.sequenceNumber() }?.stationId
            ?: filter { it.sequenceNumber() < currentSequenceNumber }
                .minByOrNull { it.sequenceNumber() }
                ?.stationId
    }

    private fun List<PlkStationOnRouteDto>.destinationRouteStationId(currentOrderNumber: Int): Int? {
        val stationsAfterCurrent = filter { it.orderNumber > currentOrderNumber }
        return stationsAfterCurrent.maxByOrNull { it.orderNumber }?.stationId
            ?: filter { it.orderNumber < currentOrderNumber }
                .minByOrNull { it.orderNumber }
                ?.stationId
    }

    private fun PlkTrainOperationDto.toDeparture(
        station: PlkOperationStationDto,
        stationNames: Map<Int, String>,
        lineLabel: String,
    ): Departure {
        val theoreticalTime = station.plannedDeparture?.toPlkOperationInstant()
            ?: station.plannedArrival?.toPlkOperationInstant()
        val estimatedTime = station.actualDeparture?.toPlkOperationInstant()
            ?: station.actualArrival?.toPlkOperationInstant()
            ?: theoreticalTime
        val stationName = stationNames[station.stationId]

        return Departure(
            id = listOf(scheduleId, orderId, trainOrderId ?: orderId, operatingDate, station.stationId).joinToString("-"),
            delayInSeconds = station.departureDelayMinutes?.times(60),
            estimatedTime = estimatedTime,
            headsign = stationName,
            lineNumber = lineLabel,
            routeId = scheduleId,
            scheduledTripStartTime = null,
            tripId = orderId,
            status = if (station.isCancelled) "X" else null,
            theoreticalTime = theoreticalTime,
            timestamp = Clock.System.now(),
            trip = (trainOrderId ?: orderId).toLong(),
            vehicleCode = null,
            vehicleId = null,
            vehicleService = lineLabel,
        )
    }
}

private const val LOG_TAG = "SkmTransitDataSource"
