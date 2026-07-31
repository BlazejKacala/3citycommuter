package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.Clock
import pl.bkacala.threecitycommuter.model.departures.Departure
import kotlin.time.Duration.Companion.minutes

internal class MockSkmRealtimeDataSource : SkmRealtimeDataSource {

    override suspend fun getDepartures(stopId: Int): List<Departure> {
        val now = Clock.System.now()
        return departuresByStopId[stopId].orEmpty().mapIndexed { index, seed ->
            val estimatedTime = now + seed.departureInMinutes.minutes
            val theoreticalTime = estimatedTime - seed.delayInSeconds.secondsAsInstantAdjustment()
            Departure(
                id = "skm-$stopId-${seed.tripId}",
                delayInSeconds = seed.delayInSeconds,
                estimatedTime = estimatedTime,
                headsign = seed.headsign,
                lineNumber = seed.lineNumber,
                routeId = seed.routeId,
                scheduledTripStartTime = now + (seed.departureInMinutes - 12).minutes,
                tripId = seed.tripId,
                status = if (seed.delayInSeconds == 0) "PLANOWO" else "REALTIME",
                theoreticalTime = theoreticalTime,
                timestamp = now + index.minutes,
                trip = seed.tripId.toLong(),
                vehicleCode = null,
                vehicleId = null,
                vehicleService = seed.lineNumber,
            )
        }
    }

    private data class MockDepartureSeed(
        val lineNumber: String,
        val routeId: Int,
        val tripId: Int,
        val headsign: String,
        val departureInMinutes: Int,
        val delayInSeconds: Int,
    )

    private companion object {
        val departuresByStopId: Map<Int, List<MockDepartureSeed>> = mapOf(
            101 to listOf(
                MockDepartureSeed("S1", 9001, 9101, "Gdynia Glowna", 4, 120),
                MockDepartureSeed("S2", 9002, 9103, "Wejherowo", 18, 0),
            ),
            102 to listOf(
                MockDepartureSeed("S1", 9001, 9101, "Gdynia Glowna", 7, 60),
                MockDepartureSeed("S1", 9003, 9102, "Gdansk Srodmiescie", 10, 0),
            ),
            103 to listOf(
                MockDepartureSeed("S1", 9001, 9101, "Gdynia Glowna", 5, 0),
                MockDepartureSeed("S2", 9004, 9104, "Gdansk Wrzeszcz", 16, 180),
            ),
            104 to listOf(
                MockDepartureSeed("S1", 9001, 9101, "Gdynia Glowna", 3, 0),
                MockDepartureSeed("S1", 9003, 9102, "Gdansk Srodmiescie", 11, 120),
            ),
            105 to listOf(
                MockDepartureSeed("S1", 9003, 9102, "Gdansk Srodmiescie", 6, 0),
                MockDepartureSeed("S2", 9002, 9103, "Wejherowo", 14, 60),
            ),
            106 to listOf(
                MockDepartureSeed("S2", 9004, 9104, "Gdansk Wrzeszcz", 8, 0),
            ),
        )
    }
}

private fun Int.secondsAsInstantAdjustment() = (this / 60).minutes
