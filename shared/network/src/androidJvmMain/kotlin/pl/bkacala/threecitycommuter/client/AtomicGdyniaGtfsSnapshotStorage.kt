package pl.bkacala.threecitycommuter.client

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/** One owner per directory. Readers see either the old complete generation or the new one. */
internal class AtomicGdyniaGtfsSnapshotStorage(
    private val directory: File,
) : GdyniaGtfsSnapshotStorage {
    private val mutex = Mutex()
    private val manifest get() = File(directory, MANIFEST)

    override suspend fun readDownloadedAtEpochMilliseconds(): Long? = mutex.withLock {
        activeDirectory()?.let(::readTimestamp)
    }

    override suspend fun readSnapshot(): GdyniaGtfsSnapshot? = mutex.withLock {
        val active = activeDirectory() ?: return@withLock null
        val timestamp = readTimestamp(active) ?: return@withLock null
        GdyniaGtfsSnapshot(
            tripsBody = File(active, TRIPS).readText(),
            gtfsZipFilePath = File(active, GTFS).absolutePath,
            downloadedAtEpochMilliseconds = timestamp,
        )
    }

    override suspend fun writeDownloadedSnapshot(
        tripsBody: String,
        downloadedAtEpochMilliseconds: Long?,
        downloadGtfsZipToPath: suspend (String) -> Unit,
        downloadGtfsZipToBytes: suspend () -> ByteArray,
        validate: suspend (GdyniaGtfsSnapshot) -> Unit,
    ): GdyniaGtfsSnapshot = mutex.withLock {
        check(directory.isDirectory || directory.mkdirs())
        val previous = activeDirectory()
        removeUnusedGenerations(setOfNotNull(previous?.name))
        val staged = File(directory, "generation-" + UUID.randomUUID())
        check(staged.mkdir())
        val pendingManifest = File(directory, "current.tmp")
        var published = false
        try {
            writeSynced(File(staged, TRIPS), tripsBody)
            val zip = File(staged, GTFS)
            downloadGtfsZipToPath(zip.absolutePath)
            require(zip.isFile && zip.length() > 0) { "Empty GTFS download" }
            FileOutputStream(zip, true).use { it.fd.sync() }
            writeSynced(File(staged, TIMESTAMP), requireNotNull(downloadedAtEpochMilliseconds).toString())
            val snapshot = GdyniaGtfsSnapshot(
                tripsBody = tripsBody,
                gtfsZipFilePath = zip.absolutePath,
                downloadedAtEpochMilliseconds = downloadedAtEpochMilliseconds,
            )
            validate(snapshot)
            currentCoroutineContext().ensureActive()
            writeSynced(pendingManifest, staged.name)
            currentCoroutineContext().ensureActive()
            // No non-atomic fallback: an unsupported filesystem must retain the old generation.
            Files.move(pendingManifest.toPath(), manifest.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            published = true
            snapshot
        } finally {
            pendingManifest.delete()
            if (!published) removeGeneration(staged)
        }
    }

    private fun activeDirectory(): File? {
        if (!manifest.exists()) return directory // Read legacy flat snapshots without rewriting them.
        val name = manifest.readText().trim()
        return if (GENERATION.matches(name)) File(directory, name).takeIf { it.isDirectory } else null
    }

    private fun readTimestamp(active: File): Long? {
        if (listOf(TRIPS, GTFS, TIMESTAMP).any { !File(active, it).isFile || File(active, it).length() == 0L }) {
            return null
        }
        return File(active, TIMESTAMP).readText().trim().toLongOrNull()
    }

    private fun removeUnusedGenerations(retained: Set<String>) {
        directory.listFiles()?.filter { GENERATION.matches(it.name) && it.name !in retained }
            ?.forEach(::removeGeneration)
    }

    private fun removeGeneration(generation: File) {
        // Delete only our known files; never recursively remove arbitrary directory contents.
        listOf(TRIPS, GTFS, TIMESTAMP).forEach { File(generation, it).delete() }
        generation.delete()
    }

    private fun writeSynced(file: File, value: String) {
        FileOutputStream(file).use { stream ->
            stream.write(value.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }
}

private const val MANIFEST = "current_generation.txt"
private const val TRIPS = "trips.json"
private const val GTFS = "gtfs.zip"
private const val TIMESTAMP = "downloaded_at_epoch_ms.txt"
private val GENERATION = Regex("generation-[0-9a-f-]{36}")
