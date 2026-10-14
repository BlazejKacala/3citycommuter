package pl.bkacala.threecitycommuter.client

internal interface GdyniaGtfsSnapshotStorage {
    /** Reads only metadata, without loading trips or the ZIP into memory. */
    suspend fun readDownloadedAtEpochMilliseconds(): Long?

    suspend fun readSnapshot(): GdyniaGtfsSnapshot?

    /** Stage privately, validate, then atomically publish. Failure must preserve the active snapshot. */
    suspend fun writeDownloadedSnapshot(
        tripsBody: String,
        downloadedAtEpochMilliseconds: Long?,
        downloadGtfsZipToPath: suspend (String) -> Unit,
        downloadGtfsZipToBytes: suspend () -> ByteArray,
        validate: suspend (GdyniaGtfsSnapshot) -> Unit,
    ): GdyniaGtfsSnapshot
}

internal data class GdyniaGtfsSnapshot(
    val tripsBody: String,
    val gtfsZip: ByteArray? = null,
    val gtfsZipFilePath: String? = null,
    val downloadedAtEpochMilliseconds: Long?,
)
