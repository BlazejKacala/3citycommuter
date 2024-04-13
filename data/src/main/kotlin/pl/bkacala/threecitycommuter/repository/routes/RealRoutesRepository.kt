package pl.bkacala.threecitycommuter.repository.routes

import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.route.mapToRoute
import javax.inject.Inject

class RealRoutesRepository @Inject constructor(private val networkClient: NetworkClient) : RoutesRepository {

    override suspend fun getRoute(date: String, routeId: Int, tripId: Int): Route {
        return networkClient.getRoute(date, routeId, tripId).mapToRoute()
    }
}