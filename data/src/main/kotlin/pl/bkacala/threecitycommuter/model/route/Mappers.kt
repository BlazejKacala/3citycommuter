package pl.bkacala.threecitycommuter.model.route

import pl.bkacala.threecitycommuter.model.RouteNetworkData


fun RouteNetworkData.mapToRoute() : Route {
    return Route(
        shape = this.coordinates.mapNotNull {
            if(it.size == 2) {
                Route.GeoPoint(latitude = it[1], longitude = it[0])
            } else {
                null
            }
        }
    )
}