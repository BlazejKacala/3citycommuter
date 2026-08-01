package pl.bkacala.threecitycommuter.client

import android.content.Context
import java.io.File

internal class FileBackedGdyniaGtfsSnapshotStorage(
    context: Context,
) : GdyniaGtfsSnapshotStorage {
    private val snapshotDirectory = File(context.filesDir, SNAPSHOT_DIRECTORY_NAME)
    private val tripsFile = File(snapshotDirectory, TRIPS_FILE_NAME)
    private val gtfsFile = File(snapshotDirectory, GTFS_FILE_NAME)
    private val metadataFile = File(snapshotDirectory, METADATA_FILE_NAME)

    override suspend fun readSnapshot(): GdyniaGtfsSnapshot? {
        if (!tripsFile.exists() || !gtfsFile.exists() || !metadataFile.exists()) {
            return null
        }

        return GdyniaGtfsSnapshot(
            tripsBody = tripsFile.readText(),
            gtfsZipFilePath = gtfsFile.absolutePath,
            downloadedAtEpochMilliseconds = metadataFile.readText().trim().toLongOrNull(),
        )
    }

    override suspend fun writeSnapshot(snapshot: GdyniaGtfsSnapshot): GdyniaGtfsSnapshot {
        snapshotDirectory.mkdirs()
        tripsFile.writeText(snapshot.tripsBody)
        snapshot.gtfsZip?.let { gtfsFile.writeBytes(it) }
        snapshot.gtfsZipFilePath?.takeIf { it != gtfsFile.absolutePath }?.let { sourcePath ->
            File(sourcePath).copyTo(gtfsFile, overwrite = true)
        }
        metadataFile.writeText(snapshot.downloadedAtEpochMilliseconds?.toString().orEmpty())
        return GdyniaGtfsSnapshot(
            tripsBody = snapshot.tripsBody,
            gtfsZipFilePath = gtfsFile.absolutePath,
            downloadedAtEpochMilliseconds = snapshot.downloadedAtEpochMilliseconds,
        )
    }

    override suspend fun writeDownloadedSnapshot(
        tripsBody: String,
        downloadedAtEpochMilliseconds: Long?,
        downloadGtfsZipToPath: suspend (String) -> Unit,
        downloadGtfsZipToBytes: suspend () -> ByteArray,
    ): GdyniaGtfsSnapshot {
        snapshotDirectory.mkdirs()
        tripsFile.writeText(tripsBody)
        downloadGtfsZipToPath(gtfsFile.absolutePath)
        metadataFile.writeText(downloadedAtEpochMilliseconds?.toString().orEmpty())
        return GdyniaGtfsSnapshot(
            tripsBody = tripsBody,
            gtfsZipFilePath = gtfsFile.absolutePath,
            downloadedAtEpochMilliseconds = downloadedAtEpochMilliseconds,
        )
    }
}

private const val SNAPSHOT_DIRECTORY_NAME = "gdynia_gtfs"
private const val TRIPS_FILE_NAME = "trips.json"
private const val GTFS_FILE_NAME = "gtfs.zip"
private const val METADATA_FILE_NAME = "downloaded_at_epoch_ms.txt"
