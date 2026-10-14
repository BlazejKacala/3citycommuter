package pl.bkacala.threecitycommuter.ui.screen.map

import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.time.Duration.Companion.seconds

/** Always recalculates from the original timetable, never from corrected times. */
internal class RouteStopTimeCalculator {
    fun calculate(
        route: Route,
        selectedStopKey: TransitStopKey?,
        selectedDepartureTime: Instant?,
        delayInSeconds: Int? = null,
        estimatedTime: Instant? = null,
        liveDelayInSeconds: Int? = null,
    ): List<Route.Stop> {
        val routeSelectedStopTime = route.stops.firstOrNull { it.key == selectedStopKey }?.expectedTime
        val scheduledTime = selectedDepartureTime
            ?: estimatedTime?.let { estimate -> delayInSeconds?.let { estimate.minus(it.seconds) } }
            ?: routeSelectedStopTime
        val expectedTime = if (liveDelayInSeconds != null && scheduledTime != null) {
            scheduledTime.plus(liveDelayInSeconds.seconds)
        } else {
            estimatedTime ?: selectedDepartureTime?.plus((delayInSeconds ?: 0).seconds)
        }
        val correction = if (expectedTime != null && routeSelectedStopTime != null) {
            expectedTime - routeSelectedStopTime
        } else {
            (liveDelayInSeconds ?: delayInSeconds ?: 0).seconds
        }
        return route.stops.map { stop ->
            stop.copy(
                expectedTime = if (stop.key == selectedStopKey) {
                    expectedTime ?: stop.expectedTime?.plus(correction)
                } else {
                    stop.expectedTime?.plus(correction)
                },
            )
        }
    }
}
