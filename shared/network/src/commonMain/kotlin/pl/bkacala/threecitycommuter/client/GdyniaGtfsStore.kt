package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.time.Duration.Companion.minutes

internal class GdyniaGtfsStore(
    private val httpClient: HttpClient,
    private val json: Json,
    private val zipEntryReader: ZipEntryReader,
    private val snapshotStorage: GdyniaGtfsSnapshotStorage,
    private val seedSource: GdyniaGtfsSeedSource,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GdyniaGtfsPreloader {
    // All load, refresh and publication paths use this coordinator. Waiters reuse the result;
    // cancelling the owner aborts its import, while cancelling a waiter leaves the owner alone.
    private val cacheMutex = Mutex()
    private val publishedState = MutableStateFlow(CacheState())
    private var state: CacheState
        get() = publishedState.value
        set(value) {
            publishedState.value = value
        }
    private var persistedSnapshotRejected = false
    private var retryAfter: Instant? = null
    private val missingTrips = linkedMapOf<Int, Instant>()

    private suspend fun <T> withCache(block: suspend () -> T): T = withContext(dispatcher) {
        cacheMutex.withLock { block() }
    }

    suspend fun getRouteForTrip(tripId: Int): Route? = withContext(dispatcher) {
        // Already available data stays usable while a new generation is being imported.
        val cache = state.shape?.takeIf { it.hasCompleteTrip(tripId) } ?: withCache {
            var loaded = ensureShapeCacheLoaded()
            if (!loaded.hasCompleteTrip(tripId) && !isKnownMissing(tripId)) {
                if (!hasFreshSnapshot()) {
                    loadNetworkCacheOrNull()?.let(::applyFullCache)
                    loaded = state.shape ?: loaded
                }
                if (!loaded.hasCompleteTrip(tripId)) rememberMissing(tripId)
            }
            loaded
        }
        val shapeId = cache.shapeIdByTripId[tripId] ?: return@withContext null
        cache.routeByShapeId[shapeId]?.copy(stops = cache.routeStopsByTripId[tripId].orEmpty())
    }

    /**
     * Returns the currently published GTFS generation for timetable parsing.
     * Refresh/publication stays serialized with departures and shape loading.
     * If refresh fails, the last published disk generation remains readable.
     */
    suspend fun getTimetableSnapshot(): GdyniaGtfsSnapshot? = withContext(dispatcher) {
        cacheMutex.withLock {
            if (!hasFreshSnapshot()) {
                loadTimetableSnapshotOrNull()?.let(::applyPublishedTimetableSnapshot)
            }
            snapshotStorage.readSnapshot()
        }
    }

    private fun ShapeCache.hasCompleteTrip(tripId: Int): Boolean =
        shapeIdByTripId[tripId]?.let { it in routeByShapeId } == true &&
            !routeStopsByTripId[tripId].isNullOrEmpty()

    private fun isKnownMissing(tripId: Int): Boolean {
        val expiry = missingTrips.remove(tripId) ?: return false
        if (clock.now() >= expiry) return false
        missingTrips[tripId] = expiry
        return true
    }

    private fun rememberMissing(tripId: Int) {
        missingTrips[tripId] = clock.now() + MISSING_TRIP_TTL
        while (missingTrips.size > MAX_MISSING_TRIPS) missingTrips.remove(missingTrips.keys.first())
    }

    suspend fun resolveTripId(
        stopId: Int,
        departureTime: String?,
        headsign: String?,
        fallbackTripId: Int,
    ): Int = withContext(dispatcher) {
        val normalizedDepartureTime = departureTime?.shortTime() ?: return@withContext fallbackTripId
        val currentCache = state.departure ?: withCache { ensureDepartureCacheLoaded() }
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

    override suspend fun preload() = withCache {
        // Fresh disk metadata avoids any startup deserialization.
        if (!hasFreshSnapshot()) ensureDepartureCacheLoaded()
        Unit
    }

    override suspend fun refresh() = withCache {
        if (!hasFreshSnapshot()) {
            val cache = loadNetworkCacheOrNull()
            if (cache != null) {
                applyFullCache(cache)
            } else if (state.departure == null && state.shape == null) {
                // refresh() may be called before preload(), including when offline.
                ensureDepartureCacheLoaded()
            }
        }
    }

    private suspend fun ensureDepartureCacheLoaded(): DepartureCache {
        state.departure?.let { return it }
        val local = loadPersistedDepartureCache() ?: loadBundledDepartureCache()
        if (local != null) {
            state = state.copy(departure = local)
            return local
        }
        return loadNetworkCache().also(::applyFullCache).departure
    }

    private suspend fun ensureShapeCacheLoaded(): ShapeCache {
        state.shape?.let { return it }
        return loadPersistedCache()?.also(::applyFullCache)?.shape
            ?: loadBundledShapeCache()?.also { state = state.copy(shape = it) }
            ?: loadNetworkCache().also(::applyFullCache).shape
    }

    private suspend fun loadNetworkCache(): FullCache =
        loadNetworkCacheOrNull()
            ?: error("Gdynia GTFS data unavailable: no local seed and network refresh failed")

    private suspend fun loadNetworkCacheOrNull(): FullCache? {
        if (retryAfter?.let { clock.now() < it } == true) return null
        return try {
            var validated: FullCache? = null
            downloadSnapshot { snapshot ->
                validated = buildFullCache(snapshot).also(::validateFullCache)
            }
            retryAfter = null
            logInfo(LOG_TAG, "Published validated Gdynia GTFS snapshot")
            checkNotNull(validated)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            retryAfter = clock.now() + RETRY_DELAY
            logError(LOG_TAG, "Failed to import Gdynia GTFS; keeping previous generation", exception)
            null
        }
    }

    /**
     * Timetable details only need the published archive. Validate the feed without materializing
     * route geometry or per-stop domain objects, which are loaded lazily by their consumers.
     */
    private suspend fun loadTimetableSnapshotOrNull(): GdyniaGtfsSnapshot? {
        if (retryAfter?.let { clock.now() < it } == true) return null
        return try {
            val snapshot = downloadSnapshot(::validateTimetableSnapshot)
            retryAfter = null
            logInfo(LOG_TAG, "Published validated Gdynia GTFS timetable snapshot")
            snapshot
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            retryAfter = clock.now() + RETRY_DELAY
            logError(LOG_TAG, "Failed to import Gdynia GTFS timetable; keeping previous generation", exception)
            null
        }
    }

    private fun applyPublishedTimetableSnapshot(snapshot: GdyniaGtfsSnapshot) {
        // These caches belong to the preceding disk generation. Clear them so later route and
        // departure reads rebuild from the newly published snapshot.
        state = CacheState(downloadedAt = snapshot.downloadedAtEpochMilliseconds?.let(Instant::fromEpochMilliseconds))
        persistedSnapshotRejected = false
        missingTrips.clear()
    }

    private suspend fun validateTimetableSnapshot(snapshot: GdyniaGtfsSnapshot) {
        val tripShapes = json.decodeFromString<List<GdyniaTripNetworkData>>(snapshot.tripsBody)
            .associate { it.tripId to it.shapeId }
        require(tripShapes.isNotEmpty()) { "Empty trips" }

        val tripsWithStops = mutableSetOf<Int>()
        var tripIdIndex = -1
        var stopIdIndex = -1
        var stopSequenceIndex = -1
        var departureTimeIndex = -1
        var stopTimesCount = 0
        val hasStopTimes = readGtfsEntryLines(snapshot, STOP_TIMES_ENTRY_NAME) { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@readGtfsEntryLines
            val columns = line.parseCsvLine()
            if (tripIdIndex == -1) {
                tripIdIndex = columns.indexOf("tripId").takeIf { it != -1 } ?: columns.indexOf("trip_id")
                stopIdIndex = columns.indexOf("stopId").takeIf { it != -1 } ?: columns.indexOf("stop_id")
                stopSequenceIndex = columns.indexOf("stopSequence").takeIf { it != -1 } ?: columns.indexOf("stop_sequence")
                departureTimeIndex = columns.indexOf("departureTime").takeIf { it != -1 } ?: columns.indexOf("departure_time")
                require(tripIdIndex != -1 && stopIdIndex != -1 && stopSequenceIndex != -1 && departureTimeIndex != -1) {
                    "Invalid stop_times header, required columns are missing"
                }
                return@readGtfsEntryLines
            }

            val tripId = columns.getOrNull(tripIdIndex)?.toIntOrNull() ?: error("Invalid GTFS trip_id")
            columns.getOrNull(stopIdIndex)?.toIntOrNull() ?: error("Invalid GTFS stop_id")
            columns.getOrNull(stopSequenceIndex)?.toIntOrNull() ?: error("Invalid GTFS stop_sequence")
            require(!columns.getOrNull(departureTimeIndex).isNullOrBlank()) { "Missing departure time" }
            require(tripId in tripShapes) { "stop_times references an unknown trip" }
            tripsWithStops += tripId
            stopTimesCount++
        }
        require(hasStopTimes && tripIdIndex != -1) { "Missing or empty $STOP_TIMES_ENTRY_NAME" }
        require(stopTimesCount > 0) { "Empty stop_times" }

        val shapeIds = mutableSetOf<Int>()
        var shapeIdIndex = -1
        var latitudeIndex = -1
        var longitudeIndex = -1
        var shapeSequenceIndex = -1
        val hasShapes = readGtfsEntryLines(snapshot, SHAPES_ENTRY_NAME) { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@readGtfsEntryLines
            val columns = line.parseCsvLine()
            if (shapeIdIndex == -1) {
                shapeIdIndex = columns.indexOf("shape_id")
                latitudeIndex = columns.indexOf("shape_pt_lat")
                longitudeIndex = columns.indexOf("shape_pt_lon")
                shapeSequenceIndex = columns.indexOf("shape_pt_sequence")
                require(shapeIdIndex != -1 && latitudeIndex != -1 && longitudeIndex != -1 && shapeSequenceIndex != -1) {
                    "Invalid shapes.txt header, required columns are missing"
                }
                return@readGtfsEntryLines
            }

            val shapeId = columns.getOrNull(shapeIdIndex)?.toIntOrNull() ?: error("Invalid GTFS shape_id")
            columns.getOrNull(latitudeIndex)?.toDoubleOrNull() ?: error("Invalid GTFS shape latitude")
            columns.getOrNull(longitudeIndex)?.toDoubleOrNull() ?: error("Invalid GTFS shape longitude")
            columns.getOrNull(shapeSequenceIndex)?.toIntOrNull() ?: error("Invalid GTFS shape sequence")
            shapeIds += shapeId
        }
        require(hasShapes && shapeIdIndex != -1 && shapeIds.isNotEmpty()) { "Missing or empty $SHAPES_ENTRY_NAME" }
        require(tripShapes.all { (tripId, shapeId) -> shapeId in shapeIds && tripId in tripsWithStops }) {
            "Incomplete GTFS generation: trips, shapes and stop_times disagree"
        }
    }

    private fun validateFullCache(cache: FullCache) {
        require(cache.departure.stopTimesByStopId.isNotEmpty()) { "Empty stop_times" }
        require(cache.shape.shapeIdByTripId.isNotEmpty()) { "Empty trips" }
        require(cache.shape.routeByShapeId.isNotEmpty()) { "Empty shapes" }
        require(
            cache.shape.shapeIdByTripId.all { (trip, shape) ->
                shape in cache.shape.routeByShapeId && !cache.shape.routeStopsByTripId[trip].isNullOrEmpty()
            },
        ) { "Incomplete GTFS generation: trips, shapes and stop_times disagree" }
    }

    private suspend fun hasFreshSnapshot(): Boolean {
        if (persistedSnapshotRejected) return false
        val timestamp = state.downloadedAt ?: try {
            snapshotStorage.readDownloadedAtEpochMilliseconds()?.let(Instant::fromEpochMilliseconds)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            logError(LOG_TAG, "Failed to read Gdynia snapshot metadata", exception)
            null
        }
        val now = clock.now()
        return timestamp != null && now >= timestamp && now < timestamp + CACHE_TTL
    }

    private suspend fun loadPersistedDepartureCache(): DepartureCache? = try {
        snapshotStorage.readSnapshot()?.let { snapshot ->
            val stops = parseStopTimes(snapshot, includeRouteStops = false)
            state = state.copy(downloadedAt = snapshot.downloadedAtEpochMilliseconds?.let(Instant::fromEpochMilliseconds))
            DepartureCache(stops.stopTimesByStopId)
        }
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        persistedSnapshotRejected = true
        logError(LOG_TAG, "Failed to load persisted Gdynia departures; using seed", exception)
        null
    }

    private suspend fun loadPersistedCache(): FullCache? = try {
        snapshotStorage.readSnapshot()?.let { buildFullCache(it) }
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

    private suspend fun downloadSnapshot(validate: suspend (GdyniaGtfsSnapshot) -> Unit): GdyniaGtfsSnapshot {
        val tripsBody = httpClient.get(TRIPS_URL).body<String>()
        return snapshotStorage.writeDownloadedSnapshot(
            tripsBody = tripsBody,
            downloadedAtEpochMilliseconds = clock.now().toEpochMilliseconds(),
            downloadGtfsZipToPath = { filePath ->
                downloadUrlToFile(httpClient, GTFS_ZIP_URL, filePath)
            },
            downloadGtfsZipToBytes = {
                httpClient.get(GTFS_ZIP_URL).body<ByteArray>()
            },
            validate = validate,
        )
    }

    private suspend fun buildFullCache(snapshot: GdyniaGtfsSnapshot): FullCache {
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
        state = CacheState(fullCache.departure, fullCache.shape, fullCache.downloadedAt)
        persistedSnapshotRejected = false
        missingTrips.clear()
    }

    private suspend fun parseTrips(content: String): Map<Int, Int> {
        val context = currentCoroutineContext()
        context.ensureActive()
        return json.decodeFromString<List<GdyniaTripNetworkData>>(content).associate {
            context.ensureActive()
            it.tripId to it.shapeId
        }
    }

    private suspend fun parseShapes(snapshot: GdyniaGtfsSnapshot): Map<Int, Route> {
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
            val shapeId = columns.getOrNull(shapeIdIndex)?.toIntOrNull() ?: error("Invalid GTFS numeric field")
            val latitude = columns.getOrNull(latIndex)?.toDoubleOrNull() ?: error("Invalid GTFS numeric field")
            val longitude = columns.getOrNull(lonIndex)?.toDoubleOrNull() ?: error("Invalid GTFS numeric field")
            val sequence = columns.getOrNull(sequenceIndex)?.toIntOrNull() ?: error("Invalid GTFS numeric field")
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
            error("Invalid shapes.txt header, required columns are missing")
        }

        val context = currentCoroutineContext()
        return pointsByShapeId.mapValues { (_, points) ->
            context.ensureActive()
            Route(points.sortedBy { it.sequence }.map { it.point })
        }
    }

    private suspend fun parseStopTimes(
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
            val tripId = columns.getOrNull(tripIdIndex)?.toIntOrNull() ?: error("Invalid GTFS numeric field")
            val stopId = columns.getOrNull(stopIdIndex)?.toIntOrNull() ?: error("Invalid GTFS numeric field")
            val stopSequence = columns.getOrNull(stopSequenceIndex)?.toIntOrNull() ?: error("Invalid GTFS numeric field")
            val departureTime = columns.getOrNull(departureTimeIndex).orEmpty()
            require(departureTime.isNotBlank()) { "Missing departure time" }

            stopTimesByStopId.getOrPut(stopId) { mutableListOf() }.add(
                StopTimeLookup(
                    tripId = tripId,
                    departureTime = departureTime,
                    stopHeadsign = columns.getOrNull(headsignIndex),
                ),
            )
            if (includeRouteStops) {
                routeStopsByTripId.getOrPut(tripId) { mutableListOf() }.add(
                    Route.Stop(
                        key = TransitStopKey(TransitProvider.GDYNIA, stopId),
                        sequence = stopSequence,
                        expectedTime = departureTime.toGdyniaInstantOrNull(),
                    ),
                )
            }
        }
        if (!entryRead) {
            error("Missing $STOP_TIMES_ENTRY_NAME in Gdynia GTFS archive")
        }
        if (tripIdIndex == -1 || departureTimeIndex == -1 || stopIdIndex == -1 || stopSequenceIndex == -1) {
            error("Invalid stop_times header, required columns are missing")
        }

        require(stopTimesByStopId.isNotEmpty()) { "Empty stop_times" }
        val context = currentCoroutineContext()
        return StopTimesParseResult(
            stopTimesByStopId = stopTimesByStopId,
            routeStopsByTripId = routeStopsByTripId.mapValues { (_, stops) ->
                context.ensureActive()
                stops.sortedBy { it.sequence }
            },
        )
    }

    private suspend fun readGtfsEntryLines(
        snapshot: GdyniaGtfsSnapshot,
        entryName: String,
        onLine: (String) -> Unit,
    ): Boolean {
        val context = currentCoroutineContext()
        val cancellableLine: (String) -> Unit = { line ->
            context.ensureActive()
            onLine(line)
        }
        snapshot.gtfsZipFilePath?.let { path ->
            return zipEntryReader.readEntryLines(path, entryName, cancellableLine)
        }
        snapshot.gtfsZip?.let { bytes ->
            return zipEntryReader.readEntryLines(bytes, entryName, cancellableLine)
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

    private data class CacheState(
        val departure: DepartureCache? = null,
        val shape: ShapeCache? = null,
        val downloadedAt: Instant? = null,
    )

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
}

private fun String.shortTime(): String = split(":").take(2).joinToString(":")

private fun String.normalizedHeadsign(): String = trim().lowercase()

private const val LOG_TAG = "GdyniaGtfsStore"
private val CACHE_TTL = 1.days
private val MISSING_TRIP_TTL = 5.minutes
private val RETRY_DELAY = 5.minutes
private const val MAX_MISSING_TRIPS = 128
private const val TRIPS_URL = "http://api.zdiz.gdynia.pl/pt/trips"
private const val GTFS_ZIP_URL = "http://api.zdiz.gdynia.pl/pt/gtfs.zip"
private const val SHAPES_ENTRY_NAME = "shapes.txt"
private const val STOP_TIMES_ENTRY_NAME = "stop_times.txt"
