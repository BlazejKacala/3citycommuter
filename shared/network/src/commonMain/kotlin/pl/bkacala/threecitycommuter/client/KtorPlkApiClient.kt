package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.plk.PlkOperationsResponse
import pl.bkacala.threecitycommuter.model.plk.PlkRouteDto
import pl.bkacala.threecitycommuter.model.plk.PlkScheduleResponse
import pl.bkacala.threecitycommuter.model.plk.PlkShortenedOperationsResponse
import pl.bkacala.threecitycommuter.model.plk.PlkStationsResponse
import pl.bkacala.threecitycommuter.model.plk.PlkTrainOperationDto
import pl.bkacala.threecitycommuter.model.plk.toOperationsResponse

internal class KtorPlkApiClient(
    private val httpClient: HttpClient,
    @Suppress("unused")
    private val json: Json,
) : PlkApiClient {

    override suspend fun getStations(
        search: String,
        pageSize: Int,
    ): PlkStationsResponse = httpClient.get("${PlkApiConfig.BASE_URL}/api/v1/dictionaries/stations") {
        header("X-API-Key", PlkApiConfig.API_KEY)
        parameter("search", search)
        parameter("page", 1)
        parameter("pageSize", pageSize)
    }.body()

    override suspend fun getSchedules(
        dateFrom: String,
        dateTo: String,
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean,
    ): PlkScheduleResponse = httpClient.get("${PlkApiConfig.BASE_URL}/api/v1/schedules") {
        header("X-API-Key", PlkApiConfig.API_KEY)
        parameter("dateFrom", dateFrom)
        parameter("dateTo", dateTo)
        parameter("stations", stations)
        parameter("carriersInclude", carriersInclude)
        parameter("fullRoute", fullRoutes)
        // Station names come from the local rail catalog.  The PLK dictionary can be
        // large and is not needed for departures.
        parameter("dictionaries", false)
    }.body()

    override suspend fun getOperations(
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean,
        withPlanned: Boolean,
    ): PlkOperationsResponse = httpClient.get("${PlkApiConfig.BASE_URL}/api/v1/operations") {
        header("X-API-Key", PlkApiConfig.API_KEY)
        parameter("stations", stations)
        parameter("carriersInclude", carriersInclude)
        parameter("fullRoutes", fullRoutes)
        parameter("withPlanned", withPlanned)
        parameter("page", 1)
        // A station-filtered request normally needs only a few hundred trains.  The
        // old 5000 limit made the API return and deserialize unnecessarily large
        // operation payloads, especially together with fullRoutes=true.
        parameter("pageSize", BULK_OPERATION_PAGE_SIZE)
    }.body()

    override suspend fun getOperationsShortened(
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean,
        withPlanned: Boolean,
        page: Int,
        pageSize: Int,
    ): PlkOperationsResponse = httpClient.get("${PlkApiConfig.BASE_URL}/api/v1/operations/shortened") {
        header("X-API-Key", PlkApiConfig.API_KEY)
        parameter("stations", stations)
        parameter("carriersInclude", carriersInclude)
        parameter("fullRoutes", fullRoutes)
        parameter("withPlanned", withPlanned)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.body<PlkShortenedOperationsResponse>().toOperationsResponse()

    override suspend fun getOperation(
        scheduleId: Int,
        orderId: Int,
        operatingDate: String,
    ): PlkTrainOperationDto = httpClient.get(
        "${PlkApiConfig.BASE_URL}/api/v1/operations/train/$scheduleId/$orderId/$operatingDate",
    ) {
        header("X-API-Key", PlkApiConfig.API_KEY)
        header(HttpHeaders.Accept, "application/json")
    }.body()

    override suspend fun getRoute(
        scheduleId: Int,
        orderId: Int,
    ): PlkRouteDto = httpClient.get("${PlkApiConfig.BASE_URL}/api/v1/schedules/route/$scheduleId/$orderId") {
        header("X-API-Key", PlkApiConfig.API_KEY)
        header(HttpHeaders.Accept, "application/json")
    }.body()
}

private const val BULK_OPERATION_PAGE_SIZE = 500
