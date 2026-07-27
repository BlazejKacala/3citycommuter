package pl.bkacala.threecitycommuter.client

interface GdyniaGtfsPreloader {
    suspend fun preload()

    suspend fun refresh()
}
