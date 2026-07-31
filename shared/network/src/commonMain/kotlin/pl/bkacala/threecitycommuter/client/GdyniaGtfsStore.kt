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
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.logging.logError
import pl.bkacala.threecitycommuter.logging.logInfo
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaDepartureMatchIndex
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaShapeIndex
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaShapeRouteIndexEntry
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaStopTimeIndexEntry
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaTripNetworkData
import pl.bkacala.threecitycommuter.model.gdynia.GdyniaTripShapeIndexEntry
import pl.bkacala.threecitycommuter.model.route.Route
import kotlin.time.Duration.Companion.days

internal class GdyniaGtfsStore(
    private val httpClient: HttpClient,
    private val json: Json,
    private val zipEntryReader: ZipEntryReader,
    private val snapshotStorage: GdyniaGtfsSnapshotStorage,
    private val seedSource: GdyniaGtfsSeedSource,
) : GdyniaGtfsPreloader {
    private val departureCacheMutex = Mutex()
    private val shapeCacheMutex = Mutex()
    private val refreshMutex = Mutex()

    private var departureCache: DepartureCache? = null
    private var shapeCache: ShapeCache? = null
    private var downloadedAt: Instant? = null

    suspend fun getRouteForTrip(tripId: Int): Route? =
        withContext(Dispatchers.IO) {
            val currentCache = ensureShapeCacheLoaded()
            val shapeId = currentCache.shapeIdByTripId[tripId] ?: return@withContext null
            currentCache.routeByShapeId[shapeId]
        }

    suspend fun resolveTripId(
        stopId: Int,
        departureTime: String?,
        headsign: String?,
        fallbackTripId: Int,
    ): Int = withContext(Dispatchers.IO) {
        val normalizedDepartureTime = departureTime?.shortTime() ?: return@withContext fallbackTripId
        val currentCache = ensureDepartureCacheLoaded()
        val stopTimes = currentCache.stopTimesByStopId[stopId].orEmpty()
        if (stopTimes.isEmpty()) {
            return@withContext fallbackTripId
        }

        val matchingTime = stopTimes.filter { it.departureTime.shortTime() == normalizedDepartureTime }
        if (matchingTime.isEmpty()) {
            return@withContext fallbackTripId
        }

        val normalizedHeadsign = headsign?.normalizedHeadsign()
        val matchingHeadsign = matchingTime.filter { stopTime ->
            stopTime.stopHeadsign?.normalizedHeadsign() == normalizedHeadsign
        }

        when {
            matchingHeadsign.size == 1 -> matchingHeadsign.single().tripId
            matchingTime.size == 1 -> matchingTime.single().tripId
            matchingHeadsign.isNotEmpty() -> matchingHeadsign.first().tripId
            else -> fallbackTripId
        }
    }

    override suspend fun preload() {
        withContext(Dispatchers.IO) {
            ensureDepartureCacheLoaded()
        }
    }

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            refreshFromNetworkIfStale()
        }
    }

    private suspend fun ensureDepartureCacheLoaded(): DepartureCache =
        departureCacheMutex.withLock {
            departureCache?.let { return it }

            loadBundledDepartureCache()
                ?.also { loaded ->
                    logInfo(LOG_TAG, "Loaded Gdynia departure cache from bundled departure index")
                    departureCache = loaded
                }
                ?: loadPersistedCache()
                    ?.also { loaded ->
                        logInfo(LOG_TAG, "Loaded Gdynia departure cache from persisted snapshot")
                        applyFullCache(loaded)
                    }
                    ?.departure
                ?: loadNetworkCache().also { loaded ->
                    applyFullCache(loaded)
                }.departure
        }

    private suspend fun ensureShapeCacheLoaded(): ShapeCache =
        shapeCacheMutex.withLock {
            shapeCache?.let { return it }

            loadBundledShapeCache()
                ?.also { loaded ->
                    logInfo(LOG_TAG, "Loaded Gdynia shape cache from bundled shape index")
                    shapeCache = loaded
                }
                ?: loadPersistedCache()
                    ?.also { loaded ->
                        logInfo(LOG_TAG, "Loaded Gdynia shape cache from persisted snapshot")
                        applyFullCache(loaded)
                    }
                    ?.shape
                ?: loadNetworkCache().also { loaded ->
                    applyFullCache(loaded)
                }.shape
        }

    private suspend fun refreshFromNetworkIfStale() {
        refreshMutex.withLock {
            if (downloadedAt?.plus(CACHE_TTL)?.let { it > Clock.System.now() } == true) {
                return
            }

            val networkCache = loadNetworkCacheOrNull()
            if (networkCache != null) {
                departureCacheMutex.withLock { departureCache = networkCache.departure }
                shapeCacheMutex.withLock { shapeCache = networkCache.shape }
                downloadedAt = networkCache.downloadedAt
                return
            }

            if (departureCache != null || shapeCache != null) {
                logInfo(LOG_TAG, "Keeping previously loaded Gdynia caches after refresh failure")
                return
            }

            error("Gdynia GTFS data unavailable: no local seed and network refresh failed")
        }
    }

    private suspend fun loadNetworkCache(): FullCache =
        loadNetworkCacheOrNull()
            ?: error("Gdynia GTFS data unavailable: no local seed and network refresh failed")

    private suspend fun loadNetworkCacheOrNull(): FullCache? {
        val networkSnapshot = try {
            downloadSnapshot().let { snapshot ->
                persistSnapshot(snapshot).also {
                    logInfo(LOG_TAG, "Loaded Gdynia GTFS snapshot from network")
                }
            }
        } catch (throwable: Throwable) {
            logError(LOG_TAG, "Failed to download or parse Gdynia GTFS data", throwable)
            null
        }

        return networkSnapshot?.let(::buildFullCache)
    }

    private suspend fun loadPersistedCache(): FullCache? {
        val storedSnapshot = try {
            snapshotStorage.readSnapshot()
        } catch (throwable: Throwable) {
            logError(LOG_TAG, "Failed to read persisted Gdynia GTFS snapshot", throwable)
            null
        }

        return storedSnapshot?.let(::buildFullCache)
    }

    private suspend fun loadBundledDepartureCache(): DepartureCache? =
        try {
            seedSource.readSeedDepartureMatchIndex()
                ?.let { body -> json.decodeFromString<GdyniaDepartureMatchIndex>(body) }
                ?.let(::buildDepartureCache)
        } catch (throwable: Throwable) {
            logError(LOG_TAG, "Failed to read bundled Gdynia departure match index", throwable)
            null
        }

    private suspend fun loadBundledShapeCache(): ShapeCache? =
        try {
            seedSource.readSeedShapeIndex()
                ?.let { body -> json.decodeFromString<GdyniaShapeIndex>(body) }
                ?.let(::buildShapeCache)
        } catch (throwable: Throwable) {
            logError(LOG_TAG, "Failed to read bundled Gdynia shape index", throwable)
            null
        }

    private suspend fun downloadSnapshot(): GdyniaGtfsSnapshot {
        val tripsBody = httpClient.get(TRIPS_URL).body<String>()
        return snapshotStorage.writeDownloadedSnapshot(
            tripsBody = tripsBody,
            downloadedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
            downloadGtfsZipToPath = { filePath ->
                downloadUrlToFile(httpClient, GTFS_ZIP_URL, filePath)
            },
            downloadGtfsZipToBytes = {
                httpClient.get(GTFS_ZIP_URL).body<ByteArray>()
            },
        )
    }

    private suspend fun persistSnapshot(snapshot: GdyniaGtfsSnapshot): GdyniaGtfsSnapshot {
        return try {
            snapshotStorage.writeSnapshot(snapshot)
        } catch (throwable: Throwable) {
            logError(LOG_TAG, "Failed to persist Gdynia GTFS snapshot", throwable)
            snapshot
        }
    }

    private fun buildFullCache(snapshot: GdyniaGtfsSnapshot): FullCache {
        return FullCache(
            downloadedAt = snapshot.downloadedAtEpochMilliseconds?.let(Instant::fromEpochMilliseconds),
            departure = DepartureCache(
                stopTimesByStopId = parseStopTimes(snapshot),
            ),
            shape = ShapeCache(
                shapeIdByTripId = parseTrips(snapshot.tripsBody),
                routeByShapeId = parseShapes(snapshot),
            ),
        )
    }

    private fun buildDepartureCache(index: GdyniaDepartureMatchIndex): DepartureCache =
        DepartureCache(
            stopTimesByStopId = index.stopTimeIndex.associate { it.toPair() },
        )

    private fun buildShapeCache(index: GdyniaShapeIndex): ShapeCache =
        ShapeCache(
            shapeIdByTripId = index.tripShapes.associate { it.toPair() },
            routeByShapeId = index.shapeRoutes.associate { it.toPair() },
        )

    private fun applyFullCache(fullCache: FullCache) {
        departureCache = fullCache.departure
        shapeCache = fullCache.shape
        downloadedAt = fullCache.downloadedAt
    }

    private fun parseTrips(content: String): Map<Int, Int> =
        json.decodeFromString<List<GdyniaTripNetworkData>>(content)
            .associate { it.tripId to it.shapeId }

    private fun parseShapes(snapshot: GdyniaGtfsSnapshot): Map<Int, Route> {
        val pointsByShapeId = mutableMapOf<Int, MutableList<ShapePoint>>()
        var shapeIdIndex = -1
        var latIndex = -1
        var lonIndex = -1
        var sequenceIndex = -1

        val entryRead = readGtfsEntryLines(snapshot, SHAPES_ENTRY_NAME) { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                return@readGtfsEntryLines
            }
            val columns = line.parseCsvLine()
            if (shapeIdIndex == -1) {
                shapeIdIndex = columns.indexOf("shape_id")
                latIndex = columns.indexOf("shape_pt_lat")
                lonIndex = columns.indexOf("shape_pt_lon")
                sequenceIndex = columns.indexOf("shape_pt_sequence")
                return@readGtfsEntryLines
            }
            val shapeId = columns.getOrNull(shapeIdIndex)?.toIntOrNull() ?: return@readGtfsEntryLines
            val latitude = columns.getOrNull(latIndex)?.toDoubleOrNull() ?: return@readGtfsEntryLines
            val longitude = columns.getOrNull(lonIndex)?.toDoubleOrNull() ?: return@readGtfsEntryLines
            val sequence = columns.getOrNull(sequenceIndex)?.toIntOrNull() ?: return@readGtfsEntryLines
            val point = ShapePoint(
                shapeId = shapeId,
                sequence = sequence,
                point = Route.GeoPoint(latitude = latitude, longitude = longitude),
            )
            pointsByShapeId.getOrPut(shapeId) { mutableListOf() }.add(point)
        }
        if (!entryRead) {
            error("Missing $SHAPES_ENTRY_NAME in Gdynia GTFS archive")
        }
        if (shapeIdIndex == -1 || latIndex == -1 || lonIndex == -1 || sequenceIndex == -1) {
            logError(LOG_TAG, "Invalid shapes.txt header, required columns are missing")
            return emptyMap()
        }

        return pointsByShapeId.mapValues { (_, points) ->
            Route(points.sortedBy { it.sequence }.map { it.point })
        }
    }

    private fun parseStopTimes(snapshot: GdyniaGtfsSnapshot): Map<Int, List<StopTimeLookup>> {
        val stopTimesByStopId = mutableMapOf<Int, MutableList<StopTimeLookup>>()
        var tripIdIndex = -1
        var departureTimeIndex = -1
        var stopIdIndex = -1
        var headsignIndex = -1

        val entryRead = readGtfsEntryLines(snapshot, STOP_TIMES_ENTRY_NAME) { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                return@readGtfsEntryLines
            }
            val columns = line.parseCsvLine()
            if (tripIdIndex == -1) {
                tripIdIndex = columns.indexOf("tripId").takeIf { it != -1 } ?: columns.indexOf("trip_id")
                departureTimeIndex =
                    columns.indexOf("departureTime").takeIf { it != -1 } ?: columns.indexOf("departure_time")
                stopIdIndex = columns.indexOf("stopId").takeIf { it != -1 } ?: columns.indexOf("stop_id")
                headsignIndex =
                    columns.indexOf("stopHeadsign").takeIf { it != -1 } ?: columns.indexOf("stop_headsign")
                return@readGtfsEntryLines
            }
            val tripId = columns.getOrNull(tripIdIndex)?.toIntOrNull() ?: return@readGtfsEntryLines
            val stopId = columns.getOrNull(stopIdIndex)?.toIntOrNull() ?: return@readGtfsEntryLines
            val departureTime = columns.getOrNull(departureTimeIndex).orEmpty()
            if (departureTime.isBlank()) {
                return@readGtfsEntryLines
            }

            stopTimesByStopId.getOrPut(stopId) { mutableListOf() }.add(
                StopTimeLookup(
                    tripId = tripId,
                    departureTime = departureTime,
                    stopHeadsign = columns.getOrNull(headsignIndex),
                ),
            )
        }
        if (!entryRead) {
            error("Missing $STOP_TIMES_ENTRY_NAME in Gdynia GTFS archive")
        }
        if (tripIdIndex == -1 || departureTimeIndex == -1 || stopIdIndex == -1) {
            logError(LOG_TAG, "Invalid stop_times header, required columns are missing")
            return emptyMap()
        }

        return stopTimesByStopId
    }

    private fun readGtfsEntryLines(
        snapshot: GdyniaGtfsSnapshot,
        entryName: String,
        onLine: (String) -> Unit,
    ): Boolean {
        snapshot.gtfsZipFilePath?.let { path ->
            return zipEntryReader.readEntryLines(path, entryName, onLine)
        }
        snapshot.gtfsZip?.let { bytes ->
            return zipEntryReader.readEntryLines(bytes, entryName, onLine)
        }
        return false
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

    private data class FullCache(
        val downloadedAt: Instant?,
        val departure: DepartureCache,
        val shape: ShapeCache,
    )

    private data class DepartureCache(
        val stopTimesByStopId: Map<Int, List<StopTimeLookup>>,
    )

    private data class ShapeCache(
        val shapeIdByTripId: Map<Int, Int>,
        val routeByShapeId: Map<Int, Route>,
    )

    private data class ShapePoint(
        val shapeId: Int,
        val sequence: Int,
        val point: Route.GeoPoint,
    )

    private data class StopTimeLookup(
        val tripId: Int,
        val departureTime: String,
        val stopHeadsign: String?,
    )

    private fun GdyniaTripShapeIndexEntry.toPair(): Pair<Int, Int> = tripId to shapeId

    private fun GdyniaShapeRouteIndexEntry.toPair(): Pair<Int, Route> =
        shapeId to Route(points.map { point -> Route.GeoPoint(point.latitude, point.longitude) })

    private fun GdyniaStopTimeIndexEntry.toPair(): Pair<Int, List<StopTimeLookup>> =
        stopId to departures.map { departure ->
            StopTimeLookup(
                tripId = departure.tripId,
                departureTime = departure.time,
                stopHeadsign = departure.headsign,
            )
        }
}

private fun String.shortTime(): String = split(":").take(2).joinToString(":")

private fun String.normalizedHeadsign(): String = trim().lowercase()

private const val LOG_TAG = "GdyniaGtfsStore"
private val CACHE_TTL = 1.days
private const val TRIPS_URL = "http://api.zdiz.gdynia.pl/pt/trips"
private const val GTFS_ZIP_URL = "http://api.zdiz.gdynia.pl/pt/gtfs.zip"
private const val SHAPES_ENTRY_NAME = "shapes.txt"
private const val STOP_TIMES_ENTRY_NAME = "stop_times.txt"
