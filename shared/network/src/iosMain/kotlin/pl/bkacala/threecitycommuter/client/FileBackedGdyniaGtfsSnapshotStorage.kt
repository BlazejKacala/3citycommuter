package pl.bkacala.threecitycommuter.client

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile

internal class FileBackedGdyniaGtfsSnapshotStorage : GdyniaGtfsSnapshotStorage {
    private val fileManager = NSFileManager.defaultManager
    private val mutex = Mutex()
    private val directory = resolveCachesDirectory() + "/gdynia_gtfs"
    private val manifest get() = "$directory/current_generation.txt"

    override suspend fun readDownloadedAtEpochMilliseconds(): Long? = mutex.withLock {
        activeDirectory()?.let(::readTimestamp)
    }

    override suspend fun readSnapshot(): GdyniaGtfsSnapshot? = mutex.withLock {
        val active = activeDirectory() ?: return@withLock null
        val timestamp = readTimestamp(active) ?: return@withLock null
        GdyniaGtfsSnapshot(
            tripsBody = readText("$active/$TRIPS") ?: return@withLock null,
            gtfsZipFilePath = "$active/$GTFS",
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
        check(fileManager.createDirectoryAtPath(directory, true, null, null))
        val previous = activeDirectory()
        fileManager.contentsOfDirectoryAtPath(directory, null)?.filterIsInstance<String>()
            ?.filter { GENERATION.matches(it) && "$directory/$it" != previous }
            ?.forEach { removeGeneration("$directory/$it") }
        val name = "generation-" + NSUUID().UUIDString.lowercase()
        val staged = "$directory/$name"
        check(fileManager.createDirectoryAtPath(staged, false, null, null))
        var published = false
        try {
            writeText("$staged/$TRIPS", tripsBody)
            downloadGtfsZipToPath("$staged/$GTFS")
            writeText("$staged/$TIMESTAMP", requireNotNull(downloadedAtEpochMilliseconds).toString())
            val snapshot = GdyniaGtfsSnapshot(
                tripsBody = tripsBody,
                gtfsZipFilePath = "$staged/$GTFS",
                downloadedAtEpochMilliseconds = downloadedAtEpochMilliseconds,
            )
            validate(snapshot)
            currentCoroutineContext().ensureActive()
            writeText(manifest, name)
            published = true
            snapshot
        } finally {
            if (!published) removeGeneration(staged)
        }
    }

    private fun activeDirectory(): String? {
        if (!fileManager.fileExistsAtPath(manifest)) return directory
        val name = readText(manifest)?.trim() ?: return null
        return if (GENERATION.matches(name)) "$directory/$name" else null
    }

    private fun readTimestamp(active: String): Long? {
        if (listOf(TRIPS, GTFS, TIMESTAMP).any { !fileManager.fileExistsAtPath("$active/$it") }) return null
        return readText("$active/$TIMESTAMP")?.trim()?.toLongOrNull()
    }

    private fun readText(path: String): String? =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)?.toString()

    private fun writeText(path: String, value: String) {
        check(NSString.create(string = value).writeToFile(path, true, NSUTF8StringEncoding, null))
    }

    private fun removeGeneration(path: String) {
        listOf(TRIPS, GTFS, TIMESTAMP).forEach { fileManager.removeItemAtPath("$path/$it", null) }
        if (fileManager.contentsOfDirectoryAtPath(path, null)?.isEmpty() == true) {
            fileManager.removeItemAtPath(path, null)
        }
    }

    private fun resolveCachesDirectory(): String =
        NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: error("Unable to resolve iOS caches directory")
}

private const val TRIPS = "trips.json"
private const val GTFS = "gtfs.zip"
private const val TIMESTAMP = "downloaded_at_epoch_ms.txt"
private val GENERATION = Regex("generation-[0-9a-f-]{36}")
