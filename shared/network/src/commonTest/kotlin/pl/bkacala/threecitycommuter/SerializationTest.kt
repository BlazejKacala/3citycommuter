package pl.bkacala.threecitycommuter

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.client.KtorNetworkClient
import kotlin.test.Test

class SerializationTest {

    @Test
    fun testSerialization() = runTest {
        val json = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
        val mockEngine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine = mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val apiClient = KtorNetworkClient(httpClient, json)
        apiClient.getStops()
    }
}

private val body: String = """
{
    "2024-01-09": {
        "lastUpdate": "2024-01-09 02:21:29",
        "stops": [
            {
                "stopId": 8227,
                "stopCode": "04",
                "stopName": "Dąbrowa Centrum",
                "stopShortName": "8227",
                "stopDesc": "Gdynia Dąbrowa Centrum",
                "subName": "04",
                "date": "2024-01-05",
                "zoneId": 5,
                "zoneName": "Gdynia",
                "virtual": 0,
                "nonpassenger": 0,
                "depot": 0,
                "ticketZoneBorder": 0,
                "onDemand": 0,
                "activationDate": "2024-01-05",
                "stopLat": 54.47317,
                "stopLon": 18.46509,
                "stopUrl": "",
                "locationType": null,
                "parentStation": null,
                "stopTimezone": "",
                "wheelchairBoarding": null
            }
        ]
    }
}
""".trimIndent()
