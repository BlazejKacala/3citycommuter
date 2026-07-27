package pl.bkacala.threecitycommuter.client

internal class EmptyGdyniaGtfsSeedSource : GdyniaGtfsSeedSource {
    override suspend fun readSeedSnapshot(): GdyniaGtfsSnapshot? = null
}
