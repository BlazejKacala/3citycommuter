package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.model.rail.RailNetwork
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableAvailability
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.schedule.TimetableStopTime
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

/** Station-scoped planned schedules from PLK; it does not query live operations. */
internal class RailTimetableSource(
    private val plkApiClient: PlkApiClient,
    private val railStaticCatalog: RailStaticCatalog,
) {
    private var catalogLoaded = false

    suspend fun getLines(): List<TimetableLine> {
        loadCatalog()
        return listOf(
            carrierLine(PlkApiConfig.SKM_TRICITY_CARRIER_CODE, "SKM", RailNetwork.SKM),
            carrierLine(PlkApiConfig.PKM_CARRIER_CODE, "PKM", RailNetwork.PKM),
        ).filter { it.stopKeys.isNotEmpty() }
    }

    suspend fun getTimetable(line: TimetableLine, date: LocalDate, stopKey: TransitStopKey?): LineTimetable {
        loadCatalog()
        val station = stopKey?.takeIf { it.provider == TransitProvider.PLK }
            ?: return LineTimetable(line, date, emptyList(), TimetableAvailability.UNSUPPORTED_DATE, "Wybierz stację kolejową.")
        val expectedNetwork = when (line.sourceId) {
            PlkApiConfig.SKM_TRICITY_CARRIER_CODE -> RailNetwork.SKM
            PlkApiConfig.PKM_CARRIER_CODE -> RailNetwork.PKM
            else -> return LineTimetable(line, date, emptyList(), TimetableAvailability.UNSUPPORTED_DATE)
        }
        if (railStaticCatalog.railNetworksById[station.sourceStopId] != expectedNetwork) {
            return LineTimetable(line, date, emptyList(), TimetableAvailability.UNSUPPORTED_DATE)
        }

        val response = plkApiClient.getSchedules(
            dateFrom = date.toString(),
            dateTo = date.toString(),
            stations = station.sourceStopId.toString(),
            carriersInclude = line.sourceId,
            fullRoutes = true,
        )
        val dictionaryNames = response.dictionaries?.stations.orEmpty().mapNotNull { (id, value) ->
            id.toIntOrNull()?.let { stationId -> value.name?.trim()?.takeIf(String::isNotBlank)?.let { stationId to it } }
        }.toMap()
        val names = railStaticCatalog.stopNamesById + dictionaryNames
        val journeys = response.routes.asSequence()
            .filter { it.carrierCode.equals(line.sourceId, ignoreCase = true) }
            .filter { route -> date.toString() in route.operatingDates }
            .mapNotNull { route ->
                val orderedStops = route.stations.sortedBy { it.orderNumber }
                if (orderedStops.none { it.stationId == station.sourceStopId }) return@mapNotNull null
                val stopTimes = orderedStops.mapNotNull { stop ->
                    // PLK includes route records without a passenger stop. Keep only rows
                    // with a usable time, while preserving their original order number.
                    val arrival = stop.arrivalTime?.toPlkRouteInstant(date.toString(), stop.arrivalDay ?: 0)
                    val departure = stop.departureTime?.toPlkRouteInstant(date.toString(), stop.departureDay ?: 0)
                    if (arrival == null && departure == null) return@mapNotNull null
                    TimetableStopTime(TransitStopKey(TransitProvider.PLK, stop.stationId), stop.orderNumber, arrival, departure)
                }
                if (stopTimes.isEmpty()) return@mapNotNull null
                val endStation = orderedStops.lastOrNull()?.stationId
                val headsign = endStation?.let(names::get)?.takeIf(String::isNotBlank)
                    ?: route.name?.trim()?.takeIf(String::isNotBlank)
                    ?: "Stacja PLK $endStation"
                TimetableJourney(
                    id = "plk:${route.scheduleId}:${route.orderId}:$date",
                    lineId = line.id,
                    routeId = route.scheduleId,
                    tripId = route.orderId,
                    directionId = orderedStops.joinToString(">") { it.stationId.toString() },
                    headsign = headsign,
                    stops = stopTimes,
                )
            }
            .sortedBy { it.stops.firstNotNullOfOrNull { stop -> stop.departureTime ?: stop.arrivalTime } }
            .toList()
        return LineTimetable(line, date, journeys)
    }

    private suspend fun loadCatalog() {
        if (!catalogLoaded) {
            railStaticCatalog.load()
            catalogLoaded = true
        }
    }

    private fun carrierLine(carrier: String, label: String, network: RailNetwork): TimetableLine = TimetableLine(
        id = "PLK:$carrier",
        provider = TransitProvider.PLK,
        sourceId = carrier,
        number = label,
        name = label,
        stopKeys = railStaticCatalog.stops.filter { it.railNetwork == network }.map { it.stopKey },
    )
}
