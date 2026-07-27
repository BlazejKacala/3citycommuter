package pl.bkacala.threecitycommuter.client

internal interface GdyniaGtfsSnapshotStorage {
    suspend fun readSnapshot(): GdyniaGtfsSnapshot?

    suspend fun writeSnapshot(snapshot: GdyniaGtfsSnapshot)
}

internal data class GdyniaGtfsSnapshot(
    val tripsBody: String,
    val gtfsZip: ByteArray,
    val downloadedAtEpochMilliseconds: Long?,
)
