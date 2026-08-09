package pl.bkacala.threecitycommuter.ui.screen.map

import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RouteStopTimeCalculatorTest {
    @Test
    fun `applies one delay to every stop including selected stop`() {
        val firstStop = TransitStopKey(TransitProvider.GDANSK, 1)
        val secondStop = TransitStopKey(TransitProvider.GDANSK, 2)
        val firstTime = Instant.parse("2026-08-07T10:00:00Z")
        val route = Route(
            shape = emptyList(),
            stops = listOf(
                Route.Stop(firstStop, 1, firstTime),
                Route.Stop(secondStop, 2, firstTime.plus(5.minutes)),
            ),
        )

        val corrected = RouteStopTimeCalculator().calculate(
            route = route,
            selectedStopKey = firstStop,
            selectedDepartureTime = firstTime,
            delayInSeconds = 180,
        )

        assertEquals(firstTime.plus(180.seconds), corrected[0].expectedTime)
        assertEquals(firstTime.plus(8.minutes), corrected[1].expectedTime)
    }
}

private val Int.minutes get() = (this * 60).seconds
