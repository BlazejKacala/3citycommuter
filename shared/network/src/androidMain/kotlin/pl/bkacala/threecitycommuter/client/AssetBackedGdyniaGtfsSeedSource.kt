package pl.bkacala.threecitycommuter.client

import android.content.Context

internal class AssetBackedGdyniaGtfsSeedSource(
    private val context: Context,
) : GdyniaGtfsSeedSource {
    override suspend fun readSeedSnapshot(): GdyniaGtfsSnapshot =
        GdyniaGtfsSnapshot(
            tripsBody = context.assets.open(TRIPS_ASSET_PATH).bufferedReader().use { it.readText() },
            gtfsZip = context.assets.open(GTFS_ASSET_PATH).use { it.readBytes() },
            downloadedAtEpochMilliseconds = null,
        )
}

private const val TRIPS_ASSET_PATH = "gdynia/trips.json"
private const val GTFS_ASSET_PATH = "gdynia/gtfs.zip"
