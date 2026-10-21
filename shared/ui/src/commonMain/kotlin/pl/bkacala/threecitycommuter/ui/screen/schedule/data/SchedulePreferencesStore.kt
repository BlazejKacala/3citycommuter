package pl.bkacala.threecitycommuter.ui.screen.schedule.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SchedulePreferences

interface SchedulePreferencesStore {
    fun read(): SchedulePreferences
    fun write(preferences: SchedulePreferences)
}

/** Versioned production namespace, separate from earlier demonstrator preferences. */
class LocalSchedulePreferencesStore(private val settings: Settings) : SchedulePreferencesStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): SchedulePreferences = runCatching {
        val slot = settings.getInt("$KEY.active", 0)
        val count = settings.getInt("$KEY.$slot.count", 0)
        require(slot in 0..1 && count in 0..MAX_CHUNKS)
        val encoded = if (count == 0) {
            "{}"
        } else {
            buildString {
                repeat(count) { append(settings.getString("$KEY.$slot.$it", "")) }
            }
        }
        json.decodeFromString<SchedulePreferences>(encoded)
    }.getOrDefault(SchedulePreferences())

    override fun write(preferences: SchedulePreferences) {
        // JVM Preferences limits each value's size. Publish a complete alternate slot before switching readers.
        val slot = 1 - settings.getInt("$KEY.active", 0).coerceIn(0, 1)
        val previousCount = settings.getInt("$KEY.$slot.count", 0)
        val chunks = json.encodeToString(preferences).chunked(CHUNK_SIZE)
        require(chunks.size <= MAX_CHUNKS) { "Saved schedules exceed the storage limit" }
        chunks.forEachIndexed { index, chunk -> settings.putString("$KEY.$slot.$index", chunk) }
        settings.putInt("$KEY.$slot.count", chunks.size)
        settings.putInt("$KEY.active", slot)
        for (index in chunks.size until previousCount) settings.remove("$KEY.$slot.$index")
    }

    private companion object {
        const val KEY = "schedule.preferences.v2"
        const val CHUNK_SIZE = 6000
        const val MAX_CHUNKS = 256
    }
}
