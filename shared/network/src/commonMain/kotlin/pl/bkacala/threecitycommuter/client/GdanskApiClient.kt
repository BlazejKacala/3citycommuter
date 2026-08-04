package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.gdansk.GdanskDeparturesResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteShapeResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskRouteStopTimesResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskStopsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclePositionsResponse
import pl.bkacala.threecitycommuter.model.gdansk.GdanskVehiclesResponse

interface GdanskApiClient {
    suspend fun getStops(): GdanskStopsResponse

    suspend fun getDepartures(stopId: Int): GdanskDeparturesResponse

    suspend fun getVehicles(): GdanskVehiclesResponse

    suspend fun getVehiclePositions(): GdanskVehiclePositionsResponse

    suspend fun getRouteShape(date: String, routeId: Int, tripId: Int): GdanskRouteShapeResponse

    suspend fun getRouteStopTimes(date: String, routeId: Int): GdanskRouteStopTimesResponse
}
