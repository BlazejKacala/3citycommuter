package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
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
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
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
    private var persistedSnapshotRejected = false

    suspend fun getRouteForTrip(tripId: Int): Route? =
        withContext(Dispatchers.IO) {
            var currentCache = ensureShapeCacheLoaded()
            if (currentCache.routeStopsByTripId[tripId].isNullOrEmpty()) {
                refreshShapeCacheWithTripStops(tripId)?.let { refreshedCache ->
                    currentCache = refreshedCache
                }
            }
            val shapeId = currentCache.shapeIdByTripId[tripId] ?: return@withContext null
            currentCache.routeByShapeId[shapeId]?.copy(
                stops = currentCache.routeStopsByTripId[tripId].orEmpty(),
            )
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
            // A fresh disk snapshot needs no startup deserialization. Load it on first use.
            if (!hasFreshSnapshot()) ensureDepartureCacheLoaded()
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

            val local = loadPersistedDepartureCache() ?: loadBundledDepartureCache()
            if (local != null) {
                departureCache = local
                return local
            }
            loadNetworkCache().also(::applyFullCache).departure
        }

    private suspend fun ensureShapeCacheLoaded(): ShapeCache =
        shapeCacheMutex.withLock {
            shapeCache?.let { return it }

            loadPersistedCache()
                ?.also(::applyFullCache)
                ?.shape
                ?: loadBundledShapeCache()?.also { shapeCache = it }
                ?: loadNetworkCache().also(::applyFullCache).shape
        }

    private suspend fun refreshFromNetworkIfStale() {
        refreshMutex.withLock {
            if (hasFreshSnapshot()) {
                return
            }

            val networkCache = loadNetworkCacheOrNull()
            if (networkCache != null) {
                departureCacheMutex.withLock { departureCache = networkCache.departure }
                shapeCacheMutex.withLock { shapeCache = networkCache.shape }
                downloadedAt = networkCache.downloadedAt
                persistedSnapshotRejected = false
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
        } catch (throwable: Exception) {
            if (throwable is CancellationException) throw throwable
            logError(LOG_TAG, "Failed to download or parse Gdynia GTFS data", throwable)
            null
        }

        return networkSnapshot?.let(::buildFullCache)
    }

    private suspend fun hasFreshSnapshot(): Boolean {
        if (persistedSnapshotRejected) return false
        val timestamp = downloadedAt ?: try {
            snapshotStorage.readDownloadedAtEpochMilliseconds()?.let(Instant::fromEpochMilliseconds)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            logError(LOG_TAG, "Failed to read Gdynia snapshot metadata", exception)
            null
        }
        val now = Clock.System.now()
        return timestamp != null && now >= timestamp && now < timestamp + CACHE_TTL
    }

    private suspend fun loadPersistedDepartureCache(): DepartureCache? = try {
        snapshotStorage.readSnapshot()?.let { snapshot ->
            val stops = parseStopTimes(snapshot, includeRouteStops = false)
            downloadedAt = snapshot.downloadedAtEpochMilliseconds?.let(Instant::fromEpochMilliseconds)
            DepartureCache(stops.stopTimesByStopId)
        }
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        persistedSnapshotRejected = true
        logError(LOG_TAG, "Failed to load persisted Gdynia departures; using seed", exception)
        null
    }

    private suspend fun loadPersistedCache(): FullCache? = try {
        snapshotStorage.readSnapshot()?.let(::buildFullCache)
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        persistedSnapshotRejected = true
        logError(LOG_TAG, "Failed to read persisted Gdynia GTFS snapshot", exception)
        null
    }

    private suspend fun loadBundledDepartureCache(): DepartureCache? =
        try {
            seedSource.readSeedDepartureMatchIndex()
                ?.let { body -> json.decodeFromString<GdyniaDepartureMatchIndex>(body) }
                ?.let(::buildDepartureCache)
        } catch (throwable: Exception) {
            if (throwable is CancellationException) throw throwable
            logError(LOG_TAG, "Failed to read bundled Gdynia departure match index", throwable)
            null
        }

    private suspend fun loadBundledShapeCache(): ShapeCache? =
        try {
            seedSource.readSeedShapeIndex()
                ?.let { body -> json.decodeFromString<GdyniaShapeIndex>(body) }
                ?.let(::buildShapeCache)
        } catch (throwable: Exception) {
            if (throwable is CancellationException) throw throwable
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
        } catch (throwable: Exception) {
            if (throwable is CancellationException) throw throwable
            logError(LOG_TAG, "Failed to persist Gdynia GTFS snapshot", throwable)
            snapshot
        }
    }

    private fun buildFullCache(snapshot: GdyniaGtfsSnapshot): FullCache {
        val stopTimes = parseStopTimes(snapshot)
        return FullCache(
            downloadedAt = snapshot.downloadedAtEpochMilliseconds?.let(Instant::fromEpochMilliseconds),
            departure = DepartureCache(
                stopTimesByStopId = stopTimes.stopTimesByStopId,
            ),
            shape = ShapeCache(
                shapeIdByTripId = parseTrips(snapshot.tripsBody),
                routeByShapeId = parseShapes(snapshot),
                routeStopsByTripId = stopTimes.routeStopsByTripId,
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
            routeStopsByTripId = emptyMap(),
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

    private fun parseStopTimes(
        snapshot: GdyniaGtfsSnapshot,
        includeRouteStops: Boolean = true,
    ): StopTimesParseResult {
        val stopTimesByStopId = mutableMapOf<Int, MutableList<StopTimeLookup>>()
        val routeStopsByTripId = mutableMapOf<Int, MutableList<Route.Stop>>()
        var tripIdIndex = -1
        var departureTimeIndex = -1
        var stopIdIndex = -1
        var stopSequenceIndex = -1
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
                stopSequenceIndex =
                    columns.indexOf("stopSequence").takeIf { it != -1 } ?: columns.indexOf("stop_sequence")
                headsignIndex =
                    columns.indexOf("stopHeadsign").takeIf { it != -1 } ?: columns.indexOf("stop_headsign")
                return@readGtfsEntryLines
            }
            val tripId = columns.getOrNull(tripIdIndex)?.toIntOrNull() ?: return@readGtfsEntryLines
            val stopId = columns.getOrNull(stopIdIndex)?.toIntOrNull() ?: return@readGtfsEntryLines
            val stopSequence = columns.getOrNull(stopSequenceIndex)?.toIntOrNull() ?: return@readGtfsEntryLines
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
            if (includeRouteStops) routeStopsByTripId.getOrPut(tripId) { mutableListOf() }.add(
                Route.Stop(
                    key = TransitStopKey(TransitProvider.GDYNIA, stopId),
                    sequence = stopSequence,
                    expectedTime = departureTime.toGdyniaInstantOrNull(),
                ),
            )
        }
        if (!entryRead) {
            error("Missing $STOP_TIMES_ENTRY_NAME in Gdynia GTFS archive")
        }
        if (tripIdIndex == -1 || departureTimeIndex == -1 || stopIdIndex == -1 || stopSequenceIndex == -1) {
            error("Invalid stop_times header, required columns are missing")
        }

        return StopTimesParseResult(
            stopTimesByStopId = stopTimesByStopId,
            routeStopsByTripId = routeStopsByTripId.mapValues { (_, stops) -> stops.sortedBy { it.sequence } },
        )
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
        val routeStopsByTripId: Map<Int, List<Route.Stop>>,
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

    private data class StopTimesParseResult(
        val stopTimesByStopId: Map<Int, List<StopTimeLookup>>,
        val routeStopsByTripId: Map<Int, List<Route.Stop>>,
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

    private suspend fun refreshShapeCacheWithTripStops(tripId: Int): ShapeCache? =
        refreshMutex.withLock {
            val persisted = loadPersistedCache()?.shape
            if (persisted?.routeStopsByTripId?.containsKey(tripId) == true) {
                shapeCacheMutex.withLock { shapeCache = persisted }
                return persisted
            }

            val network = loadNetworkCacheOrNull()?.shape
            if (network?.routeStopsByTripId?.containsKey(tripId) == true) {
                shapeCacheMutex.withLock { shapeCache = network }
                return network
            }

            null
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
