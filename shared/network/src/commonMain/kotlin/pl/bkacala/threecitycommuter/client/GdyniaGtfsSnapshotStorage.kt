package pl.bkacala.threecitycommuter.client

internal interface GdyniaGtfsSnapshotStorage {
    suspend fun readSnapshot(): GdyniaGtfsSnapshot?

    suspend fun writeSnapshot(snapshot: GdyniaGtfsSnapshot): GdyniaGtfsSnapshot

    suspend fun writeDownloadedSnapshot(
        tripsBody: String,
        downloadedAtEpochMilliseconds: Long?,
        downloadGtfsZipToPath: suspend (String) -> Unit,
        downloadGtfsZipToBytes: suspend () -> ByteArray,
    ): GdyniaGtfsSnapshot
}

internal data class GdyniaGtfsSnapshot(
    val tripsBody: String,
    val gtfsZip: ByteArray? = null,
    val gtfsZipFilePath: String? = null,
    val downloadedAtEpochMilliseconds: Long?,
)
