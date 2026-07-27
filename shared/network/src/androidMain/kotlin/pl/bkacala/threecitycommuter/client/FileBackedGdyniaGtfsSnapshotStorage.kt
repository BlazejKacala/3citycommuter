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
            gtfsZip = gtfsFile.readBytes(),
            downloadedAtEpochMilliseconds = metadataFile.readText().trim().toLongOrNull(),
        )
    }

    override suspend fun writeSnapshot(snapshot: GdyniaGtfsSnapshot) {
        snapshotDirectory.mkdirs()
        tripsFile.writeText(snapshot.tripsBody)
        gtfsFile.writeBytes(snapshot.gtfsZip)
        metadataFile.writeText(snapshot.downloadedAtEpochMilliseconds?.toString().orEmpty())
    }
}

private const val SNAPSHOT_DIRECTORY_NAME = "gdynia_gtfs"
private const val TRIPS_FILE_NAME = "trips.json"
private const val GTFS_FILE_NAME = "gtfs.zip"
private const val METADATA_FILE_NAME = "downloaded_at_epoch_ms.txt"
