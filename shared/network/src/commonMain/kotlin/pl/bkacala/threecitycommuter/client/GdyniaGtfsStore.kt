package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaTripNetworkData
import pl.bkacala.threecitycommuter.model.route.Route
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days

internal class GdyniaGtfsStore(
    private val httpClient: HttpClient,
    private val json: Json,
    private val zipEntryReader: ZipEntryReader,
) : GdyniaGtfsPreloader {
    private val cacheMutex = Mutex()
    private var cache: Cache? = null

    suspend fun getRouteForTrip(tripId: Int): Route? =
        withContext(Dispatchers.IO) {
            val currentCache = getCache()
            val shapeId = currentCache.shapeIdByTripId[tripId] ?: return@withContext null
            currentCache.routeByShapeId[shapeId]
        }

    override suspend fun preload() {
        withContext(Dispatchers.IO) {
            getCache()
        }
    }

    private suspend fun getCache(): Cache =
        cacheMutex.withLock {
            val current = cache
            if (current != null && current.loadedAt.plus(CACHE_TTL) > Clock.System.now()) {
                return current
            }

            loadCache().also { loaded -> cache = loaded }
        }

    private suspend fun loadCache(): Cache {
        return try {
            val tripsBody = httpClient.get(TRIPS_URL).body<String>()
            val trips = json.decodeFromString<List<GdyniaTripNetworkData>>(tripsBody)
            val gtfsZip = httpClient.get(GTFS_ZIP_URL).body<ByteArray>()
            val shapesText = zipEntryReader.readEntry(gtfsZip, SHAPES_ENTRY_NAME)
                ?: error("Missing $SHAPES_ENTRY_NAME in Gdynia GTFS archive")

            Cache(
                loadedAt = Clock.System.now(),
                shapeIdByTripId = trips.associate { it.tripId to it.shapeId },
                routeByShapeId = parseShapes(shapesText),
            )
        } catch (throwable: Throwable) {
            logError(LOG_TAG, "Failed to download or parse Gdynia GTFS data", throwable)
            throw throwable
        }
    }

    private fun parseShapes(content: String): Map<Int, Route> {
        val linesIterator = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .iterator()
        if (!linesIterator.hasNext()) {
            return emptyMap()
        }

        val header = linesIterator.next().parseCsvLine()
        val shapeIdIndex = header.indexOf("shape_id")
        val latIndex = header.indexOf("shape_pt_lat")
        val lonIndex = header.indexOf("shape_pt_lon")
        val sequenceIndex = header.indexOf("shape_pt_sequence")
        if (shapeIdIndex == -1 || latIndex == -1 || lonIndex == -1 || sequenceIndex == -1) {
            logError(LOG_TAG, "Invalid shapes.txt header, required columns are missing")
            return emptyMap()
        }

        val pointsByShapeId = mutableMapOf<Int, MutableList<ShapePoint>>()

        while (linesIterator.hasNext()) {
            val line = linesIterator.next()
            val columns = line.parseCsvLine()
            val shapeId = columns.getOrNull(shapeIdIndex)?.toIntOrNull() ?: continue
            val latitude = columns.getOrNull(latIndex)?.toDoubleOrNull() ?: continue
            val longitude = columns.getOrNull(lonIndex)?.toDoubleOrNull() ?: continue
            val sequence = columns.getOrNull(sequenceIndex)?.toIntOrNull() ?: continue
            val point = ShapePoint(
                shapeId = shapeId,
                sequence = sequence,
                point = Route.GeoPoint(latitude = latitude, longitude = longitude),
            )
            pointsByShapeId.getOrPut(shapeId) { mutableListOf() }.add(point)
        }

        return pointsByShapeId.mapValues { (_, points) ->
            Route(points.sortedBy { it.sequence }.map { it.point })
        }
    }

    private fun String.parseCsvLine(): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false

        for (char in this) {
            when {
                char == '"' -> insideQuotes = !insideQuotes
                char == ',' && !insideQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        values += current.toString()
        return values
    }

    private data class Cache(
        val loadedAt: Instant,
        val shapeIdByTripId: Map<Int, Int>,
        val routeByShapeId: Map<Int, Route>,
    )

    private data class ShapePoint(
        val shapeId: Int,
        val sequence: Int,
        val point: Route.GeoPoint,
    )
}

private const val LOG_TAG = "GdyniaGtfsStore"
private val CACHE_TTL = 1.days
private const val TRIPS_URL = "http://api.zdiz.gdynia.pl/pt/trips"
private const val GTFS_ZIP_URL = "http://api.zdiz.gdynia.pl/pt/gtfs.zip"
private const val SHAPES_ENTRY_NAME = "shapes.txt"
