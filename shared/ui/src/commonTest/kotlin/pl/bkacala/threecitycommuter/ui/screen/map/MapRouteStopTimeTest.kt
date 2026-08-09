package pl.bkacala.threecitycommuter.ui.screen.map

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MapRouteStopTimeTest {
    private val now = Instant.parse("2026-08-07T10:00:00Z")

    @Test
    fun `future stop shows rounded up minutes`() {
        assertEquals(
            "za 3 min",
            formatRouteStopTime(Instant.parse("2026-08-07T10:02:01Z"), now),
        )
    }

    @Test
    fun `current and past stops show already passed`() {
        assertEquals("na przystanku", formatRouteStopTime(now, now, isCurrentStop = true))
        assertEquals(
            "1 min temu",
            formatRouteStopTime(Instant.parse("2026-08-07T09:59:59Z"), now),
        )
    }

    @Test
    fun `missing expected time is explicit`() {
        assertEquals("brak danych", formatRouteStopTime(null, now))
    }
}
