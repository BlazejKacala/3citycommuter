package pl.bkacala.threecitycommuter.repository.update

import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock

private const val LAST_UPDATE_KEY = "last_update_key_"

internal class RealLastUpdateRepository(private val settings: Settings) :
    LastUpdateRepository {

    override fun getLastUpdateTimeStamp(key: String): Long {
        return settings.getLong("$LAST_UPDATE_KEY$key", 0)
    }

    override fun storeLastUpdateCurrentTimeStamp(key: String) {
        settings.putLong("$LAST_UPDATE_KEY$key", Clock.System.now().epochSeconds)
    }
}
