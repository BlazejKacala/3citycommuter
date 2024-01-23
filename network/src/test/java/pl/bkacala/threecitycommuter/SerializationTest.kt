package pl.bkacala.threecitycommuter

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import pl.bkacala.threecitycommuter.client.KtorNetworkClient


class SerializationTest {



    @Test
    fun testSerialization() {

        runBlocking {

            val json = Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
            val mockEngine = MockEngine { request ->
                respond(
                    content = ByteReadChannel(text = body),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val httpClient = HttpClient(engine = mockEngine) {
                install(ContentNegotiation) {
                    json(json)
                }
            }

            val apiClient = KtorNetworkClient(httpClient, json)

            val response = apiClient.getStops()



        }
    }
}

val body : String = "{\n" +
        "    \"2024-01-09\": {\n" +
        "        \"lastUpdate\": \"2024-01-09 02:21:29\",\n" +
        "        \"stops\": [\n" +
        "            {\n" +
        "                \"stopId\": 8227,\n" +
        "                \"stopCode\": \"04\",\n" +
        "                \"stopName\": \"Dąbrowa Centrum\",\n" +
        "                \"stopShortName\": \"8227\",\n" +
        "                \"stopDesc\": \"Gdynia Dąbrowa Centrum\",\n" +
        "                \"subName\": \"04\",\n" +
        "                \"date\": \"2024-01-05\",\n" +
        "                \"zoneId\": 5,\n" +
        "                \"zoneName\": \"Gdynia\",\n" +
        "                \"virtual\": 0,\n" +
        "                \"nonpassenger\": 0,\n" +
        "                \"depot\": 0,\n" +
        "                \"ticketZoneBorder\": 0,\n" +
        "                \"onDemand\": 0,\n" +
        "                \"activationDate\": \"2024-01-05\",\n" +
        "                \"stopLat\": 54.47317,\n" +
        "                \"stopLon\": 18.46509,\n" +
        "                \"stopUrl\": \"\",\n" +
        "                \"locationType\": null,\n" +
        "                \"parentStation\": null,\n" +
        "                \"stopTimezone\": \"\",\n" +
        "                \"wheelchairBoarding\": null\n" +
        "            }\n" +
        "\t\t]\n" +
        "\t}\n" +
        "}"

