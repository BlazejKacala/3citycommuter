package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.rail.RailNetwork
import pl.bkacala.threecitycommuter.model.rail.RailStationCatalog
import pl.bkacala.threecitycommuter.model.rail.RailStationSeed
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RailTimetableSourceTest {
    @Test
    fun `directions resolve stations outside local catalog and never have blank names`() = runTest {
        for ((dictionary, expected) in listOf(
            "\"101\":{\"id\":101,\"name\":\"Kartuzy\"}" to "Kartuzy",
            "\"100\":{\"id\":100,\"name\":\" \"}" to "Stacja PLK 101",
        )) {
            val client = HttpClient(
                MockEngine { request ->
                    assertEquals("true", request.url.parameters["dictionaries"])
                    respond(
                        """{"dictionaries":{"stations":{$dictionary}},"routes":[
                    {"scheduleId":7,"orderId":8,"carrierCode":"PR","name":" ","operatingDates":["2026-10-20"],"stations":[
                    {"stationId":100,"orderNumber":1,"departureTime":"12:00:00"},
                    {"stationId":101,"orderNumber":2,"arrivalTime":"13:00:00"}]}]}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            try {
                val catalog = RailStaticCatalog(object : RailStationCatalog {
                    override suspend fun getActiveStations() = listOf(RailStationSeed(100, "Start", 54.4, 18.5, RailNetwork.PKM))
                })
                val source = RailTimetableSource(KtorPlkApiClient(client, Json.Default), catalog)
                val result = source.getTimetable(source.getLines().single(), LocalDate(2026, 10, 20), TransitStopKey(TransitProvider.PLK, 100))
                assertEquals(expected, result.journeys.single().headsign)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `HTTP authentication and rate limit failures are not cached as empty schedules`() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests)) {
            var calls = 0
            val client = HttpClient(
                MockEngine {
                    calls++
                    respond("""{"message":"request rejected"}""", status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            try {
                val source = CachingPlkApiClient(KtorPlkApiClient(client, Json.Default))
                repeat(2) {
                    val error = assertFailsWith<ClientRequestException> {
                        source.getSchedules("2026-10-20", "2026-10-20", "100", "SKMT", true)
                    }
                    assertEquals(status, error.response.status)
                }
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `station schedule preserves real operating dates order and day offsets`() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("100", request.url.parameters["stations"])
                assertEquals("2026-10-20", request.url.parameters["dateFrom"])
                assertEquals("SKMT", request.url.parameters["carriersInclude"])
                respond(
                    """{"routes":[
                    {"scheduleId":7,"orderId":8,"carrierCode":"SKMT","operatingDates":["2026-10-20"],"stations":[
                        {"stationId":101,"orderNumber":2,"arrivalDay":1,"arrivalTime":"00:15:00"},
                        {"stationId":100,"orderNumber":1,"departureDay":0,"departureTime":"23:55:00"}
                    ]},
                    {"scheduleId":9,"orderId":10,"carrierCode":"SKMT","operatingDates":["2026-10-21"],"stations":[]}
                ]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val catalog = RailStaticCatalog(object : RailStationCatalog {
                override suspend fun getActiveStations() = listOf(
                    RailStationSeed(100, "Start", 54.4, 18.5, RailNetwork.SKM),
                    RailStationSeed(101, "End", 54.5, 18.6, RailNetwork.SKM),
                )
            })
            val source = RailTimetableSource(KtorPlkApiClient(client, Json.Default), catalog)
            val line = source.getLines().single()
            val journey = source.getTimetable(line, LocalDate(2026, 10, 20), TransitStopKey(TransitProvider.PLK, 100)).journeys.single()
            assertEquals(listOf(100, 101), journey.stops.map { it.stopKey.sourceStopId })
            assertEquals("100>101", journey.directionId)
            assertEquals("End", journey.headsign)
            assertEquals("2026-10-20T21:55:00Z", journey.stops.first().departureTime.toString())
            assertEquals("2026-10-20T22:15:00Z", journey.stops.last().arrivalTime.toString())
            assertNull(journey.stops.last().departureTime)
        } finally {
            client.close()
        }
    }
}
