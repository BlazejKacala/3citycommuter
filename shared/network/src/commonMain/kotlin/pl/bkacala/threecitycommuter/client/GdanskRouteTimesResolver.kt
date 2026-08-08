package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimesResponse
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.math.abs

/**
 * Resolves one concrete Gdańsk departure from the timetable response.
 *
 * Gdańsk's `tripId` and `variantId` describe a timetable pattern, not one
 * departure. Every stop sequence can therefore occur many times in one
 * response. We first select the variant, then use the same departure index
 * for every stop sequence so that times from different runs cannot mix.
 */
internal class GdanskRouteTimesResolver {
    fun resolve(
        response: GdanskRouteStopTimesResponse,
        tripId: Int,
        departureTime: Instant?,
    ): List<Route.Stop> = response.stopTimes
        // First discard other timetable patterns and technical/depot stops.
        .filter { it.tripId == tripId }
        .filterNot { it.isNonPassengerStop() }
        .let { tripStopTimes ->
            // A tripId can contain several variants. Pick the variant whose
            // first stop is closest to the selected departure's start time.
            selectVariant(tripStopTimes, departureTime)
        }
        .let { variantStopTimes ->
            // One variant contains many departures during the day. Pick one
            // departure index on the first stop and reuse it for every stop
            // sequence, keeping all times within the same run.
            selectDepartureInstance(variantStopTimes, departureTime)
        }
        .map { stopTime ->
            Route.Stop(
                key = TransitStopKey(TransitProvider.GDANSK, stopTime.stopId),
                sequence = stopTime.stopSequence,
                expectedTime = stopTime.scheduledInstant(),
            )
        }

    private fun selectVariant(
        tripStopTimes: List<GdanskRouteStopTimeResponse>,
        departureTime: Instant?,
    ): List<GdanskRouteStopTimeResponse> = tripStopTimes
        .groupBy { it.variantId }
        .values
        .minByOrNull { variantStops ->
            val firstStop = variantStops.minByOrNull { it.stopSequence }
            timeDistance(firstStop?.scheduledInstant(), departureTime)
        }
        .orEmpty()

    private fun selectDepartureInstance(
        variantStopTimes: List<GdanskRouteStopTimeResponse>,
        departureTime: Instant?,
    ): List<GdanskRouteStopTimeResponse> {
        val stopTimesBySequence = variantStopTimes.groupBy { it.stopSequence }
        val firstSequence = stopTimesBySequence.keys.minOrNull() ?: return emptyList()
        val firstStopTimes = stopTimesBySequence.getValue(firstSequence).sortedBy { it.scheduledInstant() }
        val selectedIndex = firstStopTimes.indices.minByOrNull { index ->
            timeDistance(firstStopTimes[index].scheduledInstant(), departureTime)
        } ?: 0

        return stopTimesBySequence.values
            .mapNotNull { sequenceTimes ->
                sequenceTimes
                    .sortedBy { it.scheduledInstant() }
                    .getOrNull(selectedIndex)
            }
            .sortedBy { it.stopSequence }
    }

    private fun timeDistance(candidate: Instant?, target: Instant?): Long {
        if (candidate == null || target == null) return Long.MAX_VALUE
        return abs(candidate.epochSeconds - target.epochSeconds)
    }

    private fun GdanskRouteStopTimeResponse.scheduledInstant(): Instant? =
        (departureTime ?: arrivalTime)?.toGdanskInstantOrNull()

    private fun GdanskRouteStopTimeResponse.isNonPassengerStop(): Boolean =
        passenger == false || nonpassenger == 1 || virtual == 1
}
