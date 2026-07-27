package pl.bkacala.threecitycommuter.model.gdynia

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GdyniaRouteNetworkDataSerializationTest {

    @Test
    fun `route payload deserializes with short name`() {
        val payload = """
            [
              { "routeId": 10181, "routeShortName": "181" }
            ]
        """.trimIndent()

        val routes = Json { ignoreUnknownKeys = true }.decodeFromString<List<GdyniaRouteNetworkData>>(payload)

        assertEquals(1, routes.size)
        assertEquals(10181, routes.single().routeId)
        assertEquals("181", routes.single().routeShortName)
    }
}
