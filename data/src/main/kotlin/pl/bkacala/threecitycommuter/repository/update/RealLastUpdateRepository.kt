package pl.bkacala.threecitycommuter.repository.update

import android.content.SharedPreferences
import kotlinx.datetime.Clock
import javax.inject.Inject

private const val LAST_UPDATE_KEY = "last_update_key_"

internal class RealLastUpdateRepository @Inject constructor(private val sharedPreferences: SharedPreferences) :
    LastUpdateRepository {

    override fun getLastUpdateTimeStamp(key: String): Long {
        return sharedPreferences.getLong("$LAST_UPDATE_KEY$key", 0)
    }

    override fun storeLastUpdateCurrentTimeStamp(key: String) {
        sharedPreferences.edit().putLong("$LAST_UPDATE_KEY$key", Clock.System.now().epochSeconds)
            .apply()
    }

}