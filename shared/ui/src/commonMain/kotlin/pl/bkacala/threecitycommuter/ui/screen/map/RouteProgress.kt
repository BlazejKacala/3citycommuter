package pl.bkacala.threecitycommuter.ui.screen.map

import pl.bkacala.threecitycommuter.model.LatLng
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.sphericalDistance
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

data class RouteProgress(
    val reachedSequence: Int? = null,
    val passedSequence: Int? = null,
    val currentStopKey: TransitStopKey? = null,
    val passedStopKeys: Set<TransitStopKey> = emptySet(),
)

/** Remembers observed progress; leaving a stop uses a wider radius to avoid GPS jitter. */
internal fun RouteProgress.update(
    stops: List<Route.Stop>,
    positions: Map<TransitStopKey, LatLng>,
    vehiclePosition: LatLng,
): RouteProgress {
    val nearbyStop = stops.asSequence()
        .filter { it.sequence >= (reachedSequence ?: Int.MIN_VALUE) }
        .filter { it.sequence > (passedSequence ?: Int.MIN_VALUE) }
        .mapNotNull { stop -> positions[stop.key]?.let { stop to vehiclePosition.sphericalDistance(it) } }
        .filter { it.second <= STOP_ARRIVAL_RADIUS_METERS }
        .minByOrNull { it.second }
        ?.first
    val reached = nearbyStop?.sequence ?: reachedSequence
    val reachedStop = stops.firstOrNull { it.sequence == reached }
    val distance = reachedStop?.let { positions[it.key] }?.let { vehiclePosition.sphericalDistance(it) }
    val hasLeft = distance != null && distance > STOP_DEPARTURE_RADIUS_METERS
    val passed = if (hasLeft) {
        reached
    } else {
        listOfNotNull(passedSequence, stops.filter { it.sequence < (reached ?: Int.MIN_VALUE) }.maxOfOrNull { it.sequence })
            .maxOrNull()
    }
    // A repeated stop key must remain available for a later visit on a circular route.
    val upcomingKeys = stops.filter { it.sequence > (passed ?: Int.MIN_VALUE) }.map { it.key }.toSet()
    return RouteProgress(
        reachedSequence = reached,
        passedSequence = passed,
        currentStopKey = reachedStop?.key?.takeIf { !hasLeft && reached != passed },
        passedStopKeys = stops.filter { it.sequence <= (passed ?: Int.MIN_VALUE) }
            .map { it.key }.toSet() - upcomingKeys,
    )
}

private const val STOP_ARRIVAL_RADIUS_METERS = 100.0
private const val STOP_DEPARTURE_RADIUS_METERS = 150.0
