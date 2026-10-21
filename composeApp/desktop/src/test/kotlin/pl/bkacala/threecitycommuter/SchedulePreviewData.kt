package pl.bkacala.threecitycommuter

import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.dsl.module
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.schedule.TimetableStopTime
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.schedule.ScheduleRepository
import pl.bkacala.threecitycommuter.repository.stops.TransitStopsRepository

/** Fixtures belong only to the manual preview's test source set. */
internal val schedulePreviewDataModule = module {
    single<ScheduleRepository> { PreviewScheduleRepository() }
    single<TransitStopsRepository> {
        object : TransitStopsRepository {
            override fun getTransitStops() = flowOf(previewStops)
            override fun getDepartures(stopKey: TransitStopKey) = flowOf(emptyList<Departure>())
        }
    }
    single<RoutesRepository> {
        object : RoutesRepository {
            override fun getRoute(provider: TransitProvider, routeId: Int, tripId: Int) = flowOf(Route(emptyList()))
        }
    }
    single<LocationRepository> {
        object : LocationRepository {
            override fun getLocation() = flowOf(UserLocation.default())
        }
    }
    single<PermissionChecker> {
        object : PermissionChecker {
            override fun isLocationPermissionGranted() = false
        }
    }
}

private class PreviewScheduleRepository : ScheduleRepository {
    private val line = TimetableLine("GDANSK:6", TransitProvider.GDANSK, "6", "6", "Śródmieście – Jelitkowo", previewStops.map { it.stopKey })

    override fun getLines(provider: TransitProvider) = flowOf(if (provider == TransitProvider.GDANSK) listOf(line) else emptyList())

    override fun getTimetable(line: TimetableLine, date: LocalDate, stopKey: TransitStopKey?) = flowOf(
        LineTimetable(
            line,
            date,
            (0..1).flatMap { direction ->
                (6..23).map { hour ->
                    val stops = if (direction == 0) previewStops else previewStops.reversed()
                    TimetableJourney(
                        "preview:$direction:$date:$hour",
                        line.id,
                        6,
                        direction,
                        direction.toString(),
                        stops.last().name,
                        stops.mapIndexed { index, stop ->
                            val time = LocalDateTime(date, LocalTime(hour, index * 10)).toInstant(TimeZone.of("Europe/Warsaw"))
                            TimetableStopTime(stop.stopKey, index, arrivalTime = time, departureTime = time)
                        },
                    )
                }
            },
        ),
    )
}

private val previewStops = listOf("Śródmieście", "Dworzec Główny", "Jelitkowo").mapIndexed { index, name ->
    TransitStopData(
        stopKey = TransitStopKey(TransitProvider.GDANSK, index + 1), stopCode = "0${index + 1}",
        stopName = name, stopShortName = null, stopDesc = null, subName = null, date = null,
        zoneId = 0, zoneName = null, virtual = 0, nonpassenger = 0, depot = 0, ticketZoneBorder = 0,
        onDemand = false, activationDate = null, stopLat = 54.35, stopLon = 18.65, stopUrl = null,
        locationType = null, parentStation = null, stopTimezone = "Europe/Warsaw", wheelchairBoarding = null,
        isForBuses = false, isForTrams = true,
    )
}
