package pl.bkacala.threecitycommuter.client

internal interface GdyniaGtfsSeedSource {
    suspend fun readSeedDepartureMatchIndex(): String? = null

    suspend fun readSeedShapeIndex(): String? = null
}
