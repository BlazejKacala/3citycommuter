package pl.bkacala.threecitycommuter.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AtomicGdyniaGtfsSnapshotStorageTest {
    @Test
    fun stagedGenerationIsInvisibleUntilValidationCompletes() = runTest {
        withDirectory { directory ->
            writeLegacy(directory)
            val storage = AtomicGdyniaGtfsSnapshotStorage(directory)
            val observer = AtomicGdyniaGtfsSnapshotStorage(directory)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writer = async {
                storage.publish("new") {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            assertEquals("old", observer.readSnapshot()?.tripsBody)
            assertEquals(1L, observer.readDownloadedAtEpochMilliseconds())
            release.complete(Unit)
            val published = writer.await()
            assertEquals("new", observer.readSnapshot()?.tripsBody)
            assertEquals(2L, observer.readDownloadedAtEpochMilliseconds())
            assertEquals("new zip", File(assertNotNull(published.gtfsZipFilePath)).readText())
            assertEquals("old zip", File(directory, "gtfs.zip").readText())
        }
    }

    @Test
    fun interruptedDownloadAndInvalidGenerationPreserveCommittedFiles() = runTest {
        withDirectory { directory ->
            val storage = AtomicGdyniaGtfsSnapshotStorage(directory)
            val committed = storage.publish("old")
            assertFailsWith<IOException> {
                storage.writeDownloadedSnapshot(
                    "new",
                    3,
                    downloadGtfsZipToPath = { path ->
                        File(path).writeText("partial zip")
                        throw IOException("Interrupted download")
                    },
                    downloadGtfsZipToBytes = { error("Must stream to file") },
                    validate = { error("Must not validate an interrupted download") },
                )
            }
            assertEquals(committed, storage.readSnapshot())
            assertFailsWith<IllegalArgumentException> {
                storage.publish("invalid") { throw IllegalArgumentException("Invalid feed") }
            }
            assertEquals(committed, storage.readSnapshot())
            assertEquals("old zip", File(assertNotNull(committed.gtfsZipFilePath)).readText())
            assertEquals(1, directory.listFiles()!!.count { it.isDirectory })
        }
    }

    @Test
    fun cancelledValidationRemovesStagingAndRetainsSnapshot() = runTest {
        withDirectory { directory ->
            val storage = AtomicGdyniaGtfsSnapshotStorage(directory)
            val committed = storage.publish("old")
            val entered = CompletableDeferred<Unit>()
            val writer = async {
                storage.publish("new") {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            entered.await()
            writer.cancelAndJoin()
            assertEquals(committed, storage.readSnapshot())
            assertEquals(1, directory.listFiles()!!.count { it.isDirectory })
            assertEquals("retry", storage.publish("retry").tripsBody)
        }
    }

    @Test
    fun failedManifestWriteRetainsLegacySnapshot() = runTest {
        withDirectory { directory ->
            writeLegacy(directory)
            // A directory at the temporary file path simulates a filesystem publication failure.
            assertTrue(File(directory, "current.tmp").mkdir())
            val storage = AtomicGdyniaGtfsSnapshotStorage(directory)
            assertFailsWith<IOException> { storage.publish("new") }
            assertEquals("old", storage.readSnapshot()?.tripsBody)
            assertEquals(1L, storage.readDownloadedAtEpochMilliseconds())
        }
    }

    @Test
    fun repeatedImportsKeepOnlyCurrentAndPreviousGeneration() = runTest {
        withDirectory { directory ->
            val storage = AtomicGdyniaGtfsSnapshotStorage(directory)
            repeat(5) { storage.publish("generation $it") }
            assertEquals("generation 4", storage.readSnapshot()?.tripsBody)
            assertEquals(2, directory.listFiles()!!.count { it.isDirectory })
        }
    }

    @Test
    fun restartDiscardsAbandonedStagingOnNextImport() = runTest {
        withDirectory { directory ->
            val storage = AtomicGdyniaGtfsSnapshotStorage(directory)
            storage.publish("old")
            val abandoned = File(directory, "generation-00000000-0000-0000-0000-000000000000")
            assertTrue(abandoned.mkdir())
            File(abandoned, "gtfs.zip").writeText("partial")
            val restarted = AtomicGdyniaGtfsSnapshotStorage(directory)
            assertEquals("old", restarted.readSnapshot()?.tripsBody)
            restarted.publish("new")
            assertTrue(!abandoned.exists())
            assertEquals(2, directory.listFiles()!!.count { it.isDirectory })
        }
    }

    private suspend fun AtomicGdyniaGtfsSnapshotStorage.publish(
        body: String,
        validate: suspend (GdyniaGtfsSnapshot) -> Unit = {},
    ) = writeDownloadedSnapshot(
        tripsBody = body,
        downloadedAtEpochMilliseconds = 2,
        downloadGtfsZipToPath = { File(it).writeText("$body zip") },
        downloadGtfsZipToBytes = { error("Must stream to file") },
        validate = validate,
    )

    private fun writeLegacy(directory: File) {
        File(directory, "trips.json").writeText("old")
        File(directory, "gtfs.zip").writeText("old zip")
        File(directory, "downloaded_at_epoch_ms.txt").writeText("1")
    }

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("gdynia-snapshot-test").toFile()
        try {
            block(directory)
        } finally {
            // Only these test-created files and one level of generated subdirectories are removed.
            directory.listFiles()?.forEach { child ->
                if (child.isDirectory) child.listFiles()?.forEach { it.delete() }
                child.delete()
            }
            directory.delete()
        }
    }
}
