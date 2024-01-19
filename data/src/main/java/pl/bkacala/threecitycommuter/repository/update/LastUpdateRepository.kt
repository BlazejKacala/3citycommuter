package pl.bkacala.threecitycommuter.repository.update

interface LastUpdateRepository {

    fun getLastUpdateTimeStamp(key: String): Long

    fun storeLastUpdateCurrentTimeStamp(key: String)
}