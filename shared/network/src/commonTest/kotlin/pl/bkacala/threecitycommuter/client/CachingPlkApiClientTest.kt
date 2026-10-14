package pl.bkacala.threecitycommuter.client

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.plk.PlkOperationsResponse
import pl.bkacala.threecitycommuter.model.plk.PlkRouteDto
import pl.bkacala.threecitycommuter.model.plk.PlkScheduleResponse
import pl.bkacala.threecitycommuter.model.plk.PlkStationsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours

class CachingPlkApiClientTest {
    private val source = CountingPlkClient()
    private val clock = TestClock()
    private val client = CachingPlkApiClient(source, clock)

    @Test
    fun `concurrent reads share the schedule but live operations are never cached`() = runTest {
        List(3) { async { schedule() } }.awaitAll()
        repeat(2) { client.getOperations("7500", "SKMT") }
        assertEquals(1, source.scheduleCalls)
        assertEquals(2, source.operationCalls)
    }

    @Test
    fun `schedule expires after an hour and failed refresh is not cached`() = runTest {
        schedule()
        clock.instant += 1.hours
        source.fail = true
        assertFailsWith<IllegalStateException> { schedule() }
        source.fail = false
        schedule()
        assertEquals(3, source.scheduleCalls)
    }

    @Test
    fun `cache separates date station carrier and full route flag`() = runTest {
        schedule()
        schedule(station = "5942")
        schedule(date = "2026-10-14")
        schedule(carrier = "PREG")
        schedule(fullRoute = false)
        schedule()
        assertEquals(5, source.scheduleCalls)
    }

    @Test
    fun `cache evicts the least recently used station`() = runTest {
        repeat(8) { schedule(station = it.toString()) }
        schedule(station = "0")
        schedule(station = "8")
        schedule(station = "0")
        assertEquals(9, source.scheduleCalls)
        schedule(station = "1")
        assertEquals(10, source.scheduleCalls)
    }

    private suspend fun schedule(
        station: String = "7500",
        date: String = "2026-10-13",
        carrier: String = "SKMT",
        fullRoute: Boolean = true,
    ) = client.getSchedules(date, date, station, carrier, fullRoute)
}

private class TestClock : Clock {
    var instant = Instant.parse("2026-10-13T10:00:00Z")
    override fun now(): Instant = instant
}

private class CountingPlkClient : PlkApiClient {
    var scheduleCalls = 0
    var operationCalls = 0
    var fail = false

    override suspend fun getSchedules(
        dateFrom: String,
        dateTo: String,
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean,
    ): PlkScheduleResponse {
        scheduleCalls += 1
        delay(10)
        check(!fail) { "Offline" }
        return PlkScheduleResponse()
    }

    override suspend fun getOperations(
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean,
        withPlanned: Boolean,
    ): PlkOperationsResponse {
        operationCalls += 1
        return PlkOperationsResponse()
    }

    override suspend fun getStations(search: String, pageSize: Int): PlkStationsResponse = error("Unused")
    override suspend fun getRoute(scheduleId: Int, orderId: Int): PlkRouteDto = error("Unused")
}
