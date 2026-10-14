package pl.bkacala.threecitycommuter.ui.screen.map

import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteProgressTest {
    private val first = TransitStopKey(TransitProvider.GDANSK, 1)
    private val second = TransitStopKey(TransitProvider.GDANSK, 2)
    private val stops = listOf(Route.Stop(first, 1), Route.Stop(second, 2))
    private val positions = mapOf(first to LatLng(54.0, 18.0), second to LatLng(54.01, 18.0))

    @Test
    fun leavingAnObservedStopMarksItPassedAndGpsJitterCannotUndoIt() {
        val arrived = RouteProgress().update(stops, positions, positions.getValue(first))
        assertEquals(first, arrived.currentStopKey)
        val nearby = arrived.update(stops, positions, LatLng(54.001, 18.0))
        assertEquals(first, nearby.currentStopKey)
        val left = nearby.update(stops, positions, LatLng(54.002, 18.0))
        assertEquals(setOf(first), left.passedStopKeys)
        assertNull(left.currentStopKey)
        val jitter = left.update(stops, positions, positions.getValue(first))
        assertEquals(setOf(first), jitter.passedStopKeys)
        assertNull(jitter.currentStopKey)
    }

    @Test
    fun reachingLaterStopMarksEarlierStopsPassedEvenWhenPollingSkippedThem() {
        val progress = RouteProgress().update(stops, positions, positions.getValue(second))
        assertEquals(setOf(first), progress.passedStopKeys)
        assertEquals(second, progress.currentStopKey)
    }

    @Test
    fun doesNotMarkStopsPassedBeforeObservingProgress() {
        val progress = RouteProgress().update(stops, positions, LatLng(54.005, 18.0))
        assertEquals(emptySet(), progress.passedStopKeys)
        assertNull(progress.currentStopKey)
    }

    @Test
    fun repeatedStopRemainsUpcomingUntilFinalVisit() {
        val loop = stops + Route.Stop(first, 3)
        val progress = RouteProgress().update(loop, positions, positions.getValue(second))
        assertEquals(emptySet(), progress.passedStopKeys)
        val finalVisit = progress.update(loop, positions, positions.getValue(first))
        assertEquals(3, finalVisit.reachedSequence)
        assertEquals(setOf(second), finalVisit.passedStopKeys)
    }
}
