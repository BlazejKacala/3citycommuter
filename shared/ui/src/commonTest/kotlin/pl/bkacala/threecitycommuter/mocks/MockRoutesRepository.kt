package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository

object MockRoutesRepository {
    val mockRoutesRepository = object : RoutesRepository {
        override fun getRoute(
            provider: TransitProvider,
            routeId: Int,
            tripId: Int,
        ): Flow<Route> {
            val mockPoints = listOf(
                Route.GeoPoint(54.372158, 18.638306),
                Route.GeoPoint(54.351959, 18.648064),
            )
            return flowOf(Route(shape = mockPoints))
        }
    }
}
