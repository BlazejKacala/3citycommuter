package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GdyniaTimetableSnapshotTest {
    @Test
    fun freshSnapshotIsReturnedWithoutNetworkImport() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                error("Fresh timetable snapshot should not trigger a download")
            },
        )
        try {
            val now = Instant.parse("2026-10-21T10:00:00Z")
            val existing = snapshot(now.toEpochMilliseconds())
            val storage = InMemoryGdyniaGtfsSnapshotStorage(existing)
            val store = store(client, storage, now)

            assertSame(existing, store.getTimetableSnapshot())
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun missingSnapshotIsValidatedAndPublished() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine { request ->
                requests++
                if (request.url.encodedPath.endsWith("trips")) respond(gdyniaTripsBody) else respond(gdyniaGtfsZipBytes)
            },
        )
        try {
            val now = Instant.parse("2026-10-21T10:00:00Z")
            val storage = InMemoryGdyniaGtfsSnapshotStorage()
            val store = store(client, storage, now)

            val published = store.getTimetableSnapshot()

            assertSame(storage.snapshot, published)
            assertEquals(now.toEpochMilliseconds(), published?.downloadedAtEpochMilliseconds)
            assertEquals(2, requests)
            assertTrue(published?.gtfsZip?.contentEquals(gdyniaGtfsZipBytes) == true)
        } finally {
            client.close()
        }
    }

    @Test
    fun invalidSnapshotGenerationKeepsPreviousSnapshotReadable() = runTest {
        var requests = 0
        val invalidTrips = gdyniaTripsBody.replace("\"shapeId\": 1", "\"shapeId\": 9")
        val client = HttpClient(
            MockEngine { request ->
                requests++
                if (request.url.encodedPath.endsWith("trips")) respond(invalidTrips) else respond(gdyniaGtfsZipBytes)
            },
        )
        try {
            val now = Instant.parse("2026-10-21T10:00:00Z")
            val previous = snapshot(Instant.parse("2026-10-19T10:00:00Z").toEpochMilliseconds())
            val storage = InMemoryGdyniaGtfsSnapshotStorage(previous)
            val store = store(client, storage, now)

            assertSame(previous, store.getTimetableSnapshot())
            assertSame(previous, storage.snapshot)
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    private fun snapshot(timestamp: Long) = GdyniaGtfsSnapshot(
        tripsBody = gdyniaTripsBody,
        gtfsZip = gdyniaGtfsZipBytes,
        downloadedAtEpochMilliseconds = timestamp,
    )

    private fun kotlinx.coroutines.test.TestScope.store(
        client: HttpClient,
        storage: GdyniaGtfsSnapshotStorage,
        now: Instant,
    ) = GdyniaGtfsStore(
        httpClient = client,
        json = testJson,
        zipEntryReader = ZipEntryReader(),
        snapshotStorage = storage,
        seedSource = InMemoryGdyniaGtfsSeedSource(),
        clock = object : Clock {
            override fun now() = now
        },
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}
