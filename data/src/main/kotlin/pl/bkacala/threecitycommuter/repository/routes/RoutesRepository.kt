package pl.bkacala.threecitycommuter.repository.routes

import pl.bkacala.threecitycommuter.model.route.Route

interface RoutesRepository {
    suspend fun getRoute(date: String, routeId: Int, tripId: Int): Route
}