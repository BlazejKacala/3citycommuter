package pl.bkacala.threecitycommuter.client

import pl.bkacala.threecitycommuter.model.plk.PlkOperationsResponse
import pl.bkacala.threecitycommuter.model.plk.PlkRouteDto
import pl.bkacala.threecitycommuter.model.plk.PlkScheduleResponse
import pl.bkacala.threecitycommuter.model.plk.PlkStationsResponse
import pl.bkacala.threecitycommuter.model.plk.PlkTrainOperationDto

internal interface PlkApiClient {
    suspend fun getStations(
        search: String,
        pageSize: Int = 10,
    ): PlkStationsResponse

    suspend fun getSchedules(
        dateFrom: String,
        dateTo: String,
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean = true,
    ): PlkScheduleResponse

    suspend fun getOperations(
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean = true,
        withPlanned: Boolean = true,
    ): PlkOperationsResponse

    suspend fun getOperationsShortened(
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean = false,
        withPlanned: Boolean = true,
        page: Int = 1,
        pageSize: Int = 500,
    ): PlkOperationsResponse = getOperations(
        stations = stations,
        carriersInclude = carriersInclude,
        fullRoutes = fullRoutes,
        withPlanned = withPlanned,
    )

    suspend fun getOperation(
        scheduleId: Int,
        orderId: Int,
        operatingDate: String,
    ): PlkTrainOperationDto? = null

    suspend fun getRoute(
        scheduleId: Int,
        orderId: Int,
    ): PlkRouteDto
}
