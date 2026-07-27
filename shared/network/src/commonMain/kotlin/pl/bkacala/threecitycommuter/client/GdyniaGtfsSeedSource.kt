package pl.bkacala.threecitycommuter.client

internal interface GdyniaGtfsSeedSource {
    suspend fun readSeedSnapshot(): GdyniaGtfsSnapshot?
}
