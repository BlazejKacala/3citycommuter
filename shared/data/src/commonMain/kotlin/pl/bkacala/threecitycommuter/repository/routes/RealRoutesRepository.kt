package pl.bkacala.threecitycommuter.repository.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitProvider

class RealRoutesRepository(private val transitDataSource: TransitDataSource) : RoutesRepository {

    override fun getRoute(provider: TransitProvider, routeId: Int, tripId: Int): Flow<Route> {
        return flow {
            emit(transitDataSource.getRouteShape(provider, routeId, tripId) ?: Route(emptyList()))
        }.flowOn(Dispatchers.IO)
    }
}
