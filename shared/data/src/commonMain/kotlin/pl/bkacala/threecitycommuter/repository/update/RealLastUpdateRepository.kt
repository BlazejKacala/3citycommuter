package pl.bkacala.threecitycommuter.repository.update

import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock

private const val LAST_UPDATE_KEY = "last_update_key_"

internal class RealLastUpdateRepository(private val settings: Settings) :
    LastUpdateRepository {

    override fun getLastUpdateTimeStamp(key: String): Long {
        return getLong("$LAST_UPDATE_KEY$key", 0)
    }

    override fun storeLastUpdateCurrentTimeStamp(key: String) {
        putLong("$LAST_UPDATE_KEY$key", Clock.System.now().epochSeconds)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return settings.getLong(key, defaultValue)
    }

    override fun putLong(key: String, value: Long) {
        settings.putLong(key, value)
    }
}
