package pl.bkacala.threecitycommuter.repository.update

interface LastUpdateRepository {

    fun getLastUpdateTimeStamp(key: String): Long

    fun storeLastUpdateCurrentTimeStamp(key: String)

    fun getLong(key: String, defaultValue: Long = 0): Long

    fun putLong(key: String, value: Long)
}
