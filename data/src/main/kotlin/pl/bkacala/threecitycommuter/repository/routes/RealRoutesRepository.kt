package pl.bkacala.threecitycommuter.repository.routes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pl.bkacala.threecitycommuter.client.NetworkClient
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.route.mapToRoute
import pl.bkacala.threecitycommuter.utils.toddMMyyyyString
import javax.inject.Inject

class RealRoutesRepository @Inject constructor(private val networkClient: NetworkClient) : RoutesRepository {

    override fun getRoute(routeId: Int, tripId: Int): Flow<Route> {
        val dateString =
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toddMMyyyyString()
        return flow {
            emit(networkClient.getRoute(dateString, routeId, tripId).mapToRoute())
        }

    }
}