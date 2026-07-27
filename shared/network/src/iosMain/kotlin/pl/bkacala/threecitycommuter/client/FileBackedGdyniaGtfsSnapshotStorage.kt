package pl.bkacala.threecitycommuter.client

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Foundation.stringWithContentsOfFile
import platform.posix.memcpy

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
        val gtfsData = NSData.dataWithContentsOfFile(gtfsFilePath) ?: return null

        return GdyniaGtfsSnapshot(
            tripsBody = tripsBody,
            gtfsZip = ByteArray(gtfsData.length.toInt()).apply {
                usePinned { pinned ->
                    memcpy(pinned.addressOf(0), gtfsData.bytes, gtfsData.length)
                }
            },
            downloadedAtEpochMilliseconds = NSString.stringWithContentsOfFile(
                path = metadataFilePath,
                encoding = NSUTF8StringEncoding,
                error = null,
            )?.toString()?.trim()?.toLongOrNull(),
        )
    }

    override suspend fun writeSnapshot(snapshot: GdyniaGtfsSnapshot) {
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

        snapshot.gtfsZip.usePinned { pinned ->
            NSData.dataWithBytes(
                bytes = pinned.addressOf(0),
                length = snapshot.gtfsZip.size.toULong(),
            ).writeToFile(gtfsFilePath, atomically = true)
        }

        NSString.create(string = snapshot.downloadedAtEpochMilliseconds?.toString().orEmpty()).writeToFile(
            path = metadataFilePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
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

private const val SNAPSHOT_DIRECTORY_NAME = "gdynia_gtfs"
private const val TRIPS_FILE_NAME = "trips.json"
private const val GTFS_FILE_NAME = "gtfs.zip"
private const val METADATA_FILE_NAME = "downloaded_at_epoch_ms.txt"
