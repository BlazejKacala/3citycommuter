package pl.bkacala.threecitycommuter.ui.screen.map

import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.time.Duration.Companion.seconds

/** Aligns provider timetable times with the departure selected by the user. */
internal class RouteStopTimeCalculator {
    fun calculate(
        route: Route,
        selectedStopKey: TransitStopKey?,
        selectedDepartureTime: Instant?,
    ): List<Route.Stop> {
        val routeSelectedStopTime = route.stops
            .firstOrNull { it.key == selectedStopKey }
            ?.expectedTime
        val routeTimeCorrectionSeconds = if (selectedDepartureTime != null &&
            routeSelectedStopTime != null
        ) {
            selectedDepartureTime.epochSeconds - routeSelectedStopTime.epochSeconds
        } else {
            0L
        }

        return route.stops.map { stop ->
            val correctedTime = stop.expectedTime?.plus(routeTimeCorrectionSeconds.seconds)
            stop.copy(
                expectedTime = if (stop.key == selectedStopKey) {
                    selectedDepartureTime ?: correctedTime
                } else {
                    correctedTime
                },
            )
        }
    }
}
