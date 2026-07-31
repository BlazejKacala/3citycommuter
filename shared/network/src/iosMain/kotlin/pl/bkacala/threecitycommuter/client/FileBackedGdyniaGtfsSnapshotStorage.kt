package pl.bkacala.threecitycommuter.client

import kotlinx.cinterop.refTo
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile

internal class FileBackedGdyniaGtfsSnapshotStorage : GdyniaGtfsSnapshotStorage {
    private val fileManager = NSFileManager.defaultManager
    private val snapshotDirectoryPath = buildString {
        append(resolveCachesDirectory())
        append("/")
        append(SNAPSHOT_DIRECTORY_NAME)
    }
    private val tripsFilePath = "$snapshotDirectoryPath/$TRIPS_FILE_NAME"
    private val gtfsFilePath = "$snapshotDirectoryPath/$GTFS_FILE_NAME"
    private val metadataFilePath = "$snapshotDirectoryPath/$METADATA_FILE_NAME"

    override suspend fun readSnapshot(): GdyniaGtfsSnapshot? {
        if (
            !fileManager.fileExistsAtPath(tripsFilePath) ||
            !fileManager.fileExistsAtPath(gtfsFilePath) ||
            !fileManager.fileExistsAtPath(metadataFilePath)
        ) {
            return null
        }

        val tripsBody = NSString.stringWithContentsOfFile(
            path = tripsFilePath,
            encoding = NSUTF8StringEncoding,
            error = null,
        )?.toString() ?: return null
        if (NSData.dataWithContentsOfFile(gtfsFilePath) == null) return null

        return GdyniaGtfsSnapshot(
            tripsBody = tripsBody,
            gtfsZipFilePath = gtfsFilePath,
            downloadedAtEpochMilliseconds = NSString.stringWithContentsOfFile(
                path = metadataFilePath,
                encoding = NSUTF8StringEncoding,
                error = null,
            )?.toString()?.trim()?.toLongOrNull(),
        )
    }

    override suspend fun writeSnapshot(snapshot: GdyniaGtfsSnapshot): GdyniaGtfsSnapshot {
        fileManager.createDirectoryAtPath(
            path = snapshotDirectoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        NSString.create(string = snapshot.tripsBody).writeToFile(
            path = tripsFilePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )

        snapshot.gtfsZip?.let { bytes ->
            bytes.toNSData()?.writeToFile(gtfsFilePath, atomically = true)
        }
        snapshot.gtfsZipFilePath?.takeIf { it != gtfsFilePath }?.let { sourcePath ->
            fileManager.removeItemAtPath(gtfsFilePath, error = null)
            fileManager.copyItemAtPath(sourcePath, toPath = gtfsFilePath, error = null)
        }

        NSString.create(string = snapshot.downloadedAtEpochMilliseconds?.toString().orEmpty()).writeToFile(
            path = metadataFilePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        return GdyniaGtfsSnapshot(
            tripsBody = snapshot.tripsBody,
            gtfsZipFilePath = gtfsFilePath,
            downloadedAtEpochMilliseconds = snapshot.downloadedAtEpochMilliseconds,
        )
    }

    override suspend fun writeDownloadedSnapshot(
        tripsBody: String,
        downloadedAtEpochMilliseconds: Long?,
        downloadGtfsZipToPath: suspend (String) -> Unit,
        downloadGtfsZipToBytes: suspend () -> ByteArray,
    ): GdyniaGtfsSnapshot {
        fileManager.createDirectoryAtPath(
            path = snapshotDirectoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        NSString.create(string = tripsBody).writeToFile(
            path = tripsFilePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        downloadGtfsZipToPath(gtfsFilePath)
        NSString.create(string = downloadedAtEpochMilliseconds?.toString().orEmpty()).writeToFile(
            path = metadataFilePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        return GdyniaGtfsSnapshot(
            tripsBody = tripsBody,
            gtfsZipFilePath = gtfsFilePath,
            downloadedAtEpochMilliseconds = downloadedAtEpochMilliseconds,
        )
    }

    private fun resolveCachesDirectory(): String =
        NSSearchPathForDirectoriesInDomains(
            directory = NSCachesDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String
            ?: error("Unable to resolve iOS caches directory")
}

private fun ByteArray.toNSData(): NSData =
    NSData.create(bytes = refTo(0), length = size.toULong())

private const val SNAPSHOT_DIRECTORY_NAME = "gdynia_gtfs"
private const val TRIPS_FILE_NAME = "trips.json"
private const val GTFS_FILE_NAME = "gtfs.zip"
private const val METADATA_FILE_NAME = "downloaded_at_epoch_ms.txt"
