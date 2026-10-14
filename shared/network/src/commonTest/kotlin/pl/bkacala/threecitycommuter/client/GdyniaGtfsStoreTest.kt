package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class GdyniaGtfsStoreTest {
    @Test
    fun concurrentConsumersShareOneImport() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val client = HttpClient(
            MockEngine { request ->
                requests++
                if (request.url.encodedPath.endsWith("trips")) {
                    entered.complete(Unit)
                    release.await()
                    respond(gdyniaTripsBody)
                } else {
                    respond(gdyniaGtfsZipBytes)
                }
            },
        )
        try {
            val store = store(client)
            val preload = async { store.preload() }
            entered.await()
            val departures = async { store.resolveTripId(1015, "10:22", "Kacze Buki", 999) }
            val route = async { store.getRouteForTrip(201) }
            val refresh = async { store.refresh() }
            runCurrent()
            release.complete(Unit)
            preload.await()
            refresh.await()
            assertEquals(201, departures.await())
            assertNotNull(route.await())
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellingWaiterDoesNotCancelImport() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("trips")) {
                    entered.complete(Unit)
                    release.await()
                    respond(gdyniaTripsBody)
                } else {
                    respond(gdyniaGtfsZipBytes)
                }
            },
        )
        try {
            val storage = InMemoryGdyniaGtfsSnapshotStorage()
            val store = store(client, storage)
            val owner = async { store.preload() }
            entered.await()
            val waiter = async { store.getRouteForTrip(201) }
            runCurrent()
            waiter.cancelAndJoin()
            release.complete(Unit)
            owner.await()
            assertNotNull(storage.snapshot)
            assertNotNull(store.getRouteForTrip(201))
        } finally {
            client.close()
        }
    }

    @Test
    fun missingTripsDoNotReparseOrDownloadFreshSnapshot() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                error("Unexpected request")
            },
        )
        try {
            val storage = InMemoryGdyniaGtfsSnapshotStorage(snapshot(now.toEpochMilliseconds()))
            val store = store(client, storage)
            repeat(200) { assertNull(store.getRouteForTrip(1000 + it)) }
            repeat(5) { assertNull(store.getRouteForTrip(999)) }
            assertEquals(1, storage.snapshotReads)
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun missingTripRetriesAfterExpiryAndIsFoundInNewGeneration() = runTest {
        var requests = 0
        var offline = true
        val client = HttpClient(
            MockEngine { request ->
                requests++
                if (offline) error("Offline")
                if (request.url.encodedPath.endsWith("trips")) respond(gdyniaTripsBody) else respond(gdyniaGtfsZipBytes)
            },
        )
        try {
            val seed = InMemoryGdyniaGtfsSeedSource(
                departureMatchIndex = gdyniaDepartureMatchIndexBody,
                shapeIndex = gdyniaShapeIndexBody.replace("201", "999"),
            )
            val store = store(client, seed = seed)
            assertNull(store.getRouteForTrip(201))
            repeat(3) { assertNull(store.getRouteForTrip(201)) }
            assertEquals(1, requests)
            now += 6.minutes
            offline = false
            assertNotNull(store.getRouteForTrip(201))
            assertEquals(3, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun generationChangeInvalidatesNegativeCache() = runTest {
        var offline = true
        val client = HttpClient(
            MockEngine { request ->
                if (offline) error("Offline")
                if (request.url.encodedPath.endsWith("trips")) respond(gdyniaTripsBody) else respond(gdyniaGtfsZipBytes)
            },
        )
        try {
            val seed = InMemoryGdyniaGtfsSeedSource(shapeIndex = gdyniaShapeIndexBody.replace("201", "999"))
            val store = store(client, seed = seed)
            assertNull(store.getRouteForTrip(201))
            now += 6.minutes
            // Record another miss while the retry remains blocked by the first failing retry.
            assertNull(store.getRouteForTrip(202))
            now += 5.minutes
            offline = false
            store.refresh()
            assertNotNull(store.getRouteForTrip(201))
        } finally {
            client.close()
        }
    }

    @Test
    fun invalidImportsPreserveSnapshotAndBackOff() = runTest {
        for (trips in listOf("[]", "broken JSON", gdyniaTripsBody.replace("shapeId", "missingShapeId"))) {
            var requests = 0
            val client = HttpClient(
                MockEngine { request ->
                    requests++
                    if (request.url.encodedPath.endsWith("trips")) respond(trips) else respond(gdyniaGtfsZipBytes)
                },
            )
            try {
                val previous = snapshot()
                val storage = InMemoryGdyniaGtfsSnapshotStorage(previous)
                val store = store(client, storage)
                store.preload()
                store.refresh()
                store.refresh()
                assertSame(previous, storage.snapshot)
                assertNotNull(store.getRouteForTrip(201))
                assertEquals(2, requests)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun cancelledDownloadPreservesSnapshotAndAllowsImmediateRetry() = runTest {
        val entered = CompletableDeferred<Unit>()
        var cancelDownload = true
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("trips")) {
                    respond(gdyniaTripsBody)
                } else if (cancelDownload) {
                    entered.complete(Unit)
                    awaitCancellation()
                } else {
                    respond(gdyniaGtfsZipBytes)
                }
            },
        )
        try {
            val previous = snapshot()
            val storage = InMemoryGdyniaGtfsSnapshotStorage(previous)
            val store = store(client, storage)
            store.preload()
            val refresh = async { store.refresh() }
            entered.await()
            refresh.cancelAndJoin()
            assertSame(previous, storage.snapshot)
            cancelDownload = false
            store.refresh()
            assertEquals(now.toEpochMilliseconds(), storage.snapshot?.downloadedAtEpochMilliseconds)
        } finally {
            client.close()
        }
    }

    @Test
    fun parserChecksCancellationBeforePublication() = runTest {
        val previous = snapshot()
        val memory = InMemoryGdyniaGtfsSnapshotStorage(previous)
        val storage = object : GdyniaGtfsSnapshotStorage by memory {
            override suspend fun writeDownloadedSnapshot(
                tripsBody: String,
                downloadedAtEpochMilliseconds: Long?,
                downloadGtfsZipToPath: suspend (String) -> Unit,
                downloadGtfsZipToBytes: suspend () -> ByteArray,
                validate: suspend (GdyniaGtfsSnapshot) -> Unit,
            ): GdyniaGtfsSnapshot = memory.writeDownloadedSnapshot(
                tripsBody,
                downloadedAtEpochMilliseconds,
                downloadGtfsZipToPath,
                downloadGtfsZipToBytes,
            ) {
                currentCoroutineContext().job.cancel()
                validate(it)
            }
        }
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("trips")) respond(gdyniaTripsBody) else respond(gdyniaGtfsZipBytes)
            },
        )
        try {
            val store = store(client, storage)
            val refresh = async { store.refresh() }
            assertFailsWith<CancellationException> { refresh.await() }
            assertSame(previous, memory.snapshot)
        } finally {
            client.close()
        }
    }

    @Test
    fun cachedDeparturesAndRoutesRemainAvailableDuringRefresh() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("trips")) {
                    entered.complete(Unit)
                    release.await()
                    respond(gdyniaTripsBody)
                } else {
                    respond(gdyniaGtfsZipBytes)
                }
            },
        )
        try {
            val store = store(client, InMemoryGdyniaGtfsSnapshotStorage(snapshot()))
            assertNotNull(store.getRouteForTrip(201))
            val refresh = async { store.refresh() }
            entered.await()
            assertEquals(201, store.resolveTripId(1015, "10:22", "Kacze Buki", 999))
            assertNotNull(store.getRouteForTrip(201))
            release.complete(Unit)
            refresh.await()
        } finally {
            client.close()
        }
    }

    @Test
    fun missingTripCacheEvictsOldestEntryAtItsSizeLimit() = runTest {
        val client = HttpClient(MockEngine { error("Offline") })
        try {
            val storage = InMemoryGdyniaGtfsSnapshotStorage()
            val seed = InMemoryGdyniaGtfsSeedSource(shapeIndex = gdyniaShapeIndexBody)
            val store = store(client, storage, seed)
            repeat(128) { assertNull(store.getRouteForTrip(1000 + it)) }
            val reads = storage.metadataReads
            assertNull(store.getRouteForTrip(1000)) // Touch the oldest entry.
            assertEquals(reads, storage.metadataReads)
            assertNull(store.getRouteForTrip(2000))
            assertNull(store.getRouteForTrip(1000)) // Retained by LRU.
            assertEquals(reads + 1, storage.metadataReads)
            assertNull(store.getRouteForTrip(1001)) // Evicted, so freshness is checked again.
            assertEquals(reads + 2, storage.metadataReads)
        } finally {
            client.close()
        }
    }

    private var now = Instant.parse("2026-10-14T10:00:00Z")

    private fun snapshot(timestamp: Long = 1) = GdyniaGtfsSnapshot(
        gdyniaTripsBody,
        gdyniaGtfsZipBytes,
        downloadedAtEpochMilliseconds = timestamp,
    )

    private fun TestScope.store(
        client: HttpClient,
        storage: GdyniaGtfsSnapshotStorage = InMemoryGdyniaGtfsSnapshotStorage(),
        seed: GdyniaGtfsSeedSource = InMemoryGdyniaGtfsSeedSource(),
    ) = GdyniaGtfsStore(
        client,
        testJson,
        ZipEntryReader(),
        storage,
        seed,
        clock = object : Clock {
            override fun now() = now
        },
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}
