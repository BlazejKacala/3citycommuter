package pl.bkacala.threecitycommuter.repository.routes

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitProvider

interface RoutesRepository {
    fun getRoute(provider: TransitProvider, routeId: Int, tripId: Int): Flow<Route>
}
