package pl.bkacala.threecitycommuter.repository.routes

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.route.Route

interface RoutesRepository {
    fun getRoute(routeId: Int, tripId: Int): Flow<Route>
}