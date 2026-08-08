package pl.bkacala.threecitycommuter.repository.routes

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitProvider

interface RoutesRepository {
    fun getRoute(provider: TransitProvider, routeId: Int, tripId: Int): Flow<Route>

    fun getRoute(
        provider: TransitProvider,
        routeId: Int,
        tripId: Int,
        departureTime: Instant?,
    ): Flow<Route> = getRoute(provider, routeId, tripId)
}
