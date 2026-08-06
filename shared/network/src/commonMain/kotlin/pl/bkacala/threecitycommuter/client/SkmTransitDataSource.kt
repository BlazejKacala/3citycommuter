package pl.bkacala.threecitycommuter.client

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        val (schedules, operations) = coroutineScope {
            val schedulesDeferred = async {
                plkApiClient.getSchedules(
                    dateFrom = today.toString(),
                    dateTo = today.toString(),
                    stations = stationId,
                    carriersInclude = PlkApiConfig.skmTricityCarrierCode,
                )
            }
            val operationsDeferred = async {
                plkApiClient.getOperations(
                    stations = stationId,
                    carriersInclude = PlkApiConfig.skmTricityCarrierCode,
                )
            }
            schedulesDeferred.await() to operationsDeferred.await()
        }
        val operationsByKey = operations.trains.associateBy(::routeKey)
        val stationNames = scheduleStationNamesById(schedules)

        return schedules.routes
            .asSequence()
            .filter { route -> route.carrierCode.equals(PlkApiConfig.skmTricityCarrierCode, ignoreCase = true) }
            .mapNotNull { route ->
                val routeStation = route.stations.firstOrNull { it.stationId == stopKey.sourceStopId } ?: return@mapNotNull null
                val operation = operationsByKey[routeKey(route)]
                route.toDeparture(
                    routeStation = routeStation,
                    operation = operation,
                    operationStation = operation?.stations?.firstOrNull { it.stationId == stopKey.sourceStopId },
                    stationNames = stationNames,
                    operatingDate = operation?.operatingDate ?: today.toString(),
                )
            }
            .filter { departure ->
                val departureInstant = departure.estimatedTime ?: departure.theoreticalTime
                departureInstant != null && departureInstant >= now.minus(2.minutes)
            }
            .sortedBy { it.estimatedTime ?: it.theoreticalTime }
            .toList()
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

    private fun routeKey(route: PlkRouteDto): String = routeKey(route.scheduleId, route.orderId, route.trainOrderId)

    private fun routeKey(route: PlkTrainOperationDto): String = routeKey(route.scheduleId, route.orderId, route.trainOrderId)

    private fun routeKey(
        scheduleId: Int,
        orderId: Int,
        trainOrderId: Int?,
    ): String = "$scheduleId|$orderId|${trainOrderId ?: orderId}"

    private fun PlkRouteDto.toDeparture(
        routeStation: PlkStationOnRouteDto,
        operation: PlkTrainOperationDto?,
        operationStation: PlkOperationStationDto?,
        stationNames: Map<Int, String>,
        operatingDate: String,
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
            lineNumber = nationalNumber ?: name ?: commercialCategorySymbol ?: "SKM",
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
                operation.stations
                    .filter { station -> station.sequenceNumber() > current.sequenceNumber() }
                    .maxByOrNull { it.sequenceNumber() }
                    ?.stationId
            }
        if (destinationFromOperation != null) {
            return destinationFromOperation
        }

        val destinationFromRoute = stations
            .filter { it.orderNumber > routeStation.orderNumber }
            .maxByOrNull { it.orderNumber }
            ?.stationId
        if (destinationFromRoute != null) {
            return destinationFromRoute
        }

        return stations.maxByOrNull { it.orderNumber }?.stationId
    }

    private fun PlkOperationStationDto.sequenceNumber(): Int = plannedSequenceNumber ?: actualSequenceNumber
}
