package pl.bkacala.threecitycommuter.client

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.plk.PlkScheduleResponse
import kotlin.time.Duration.Companion.hours

/** Only planned schedules are cached; live operations always reach the delegate. */
internal class CachingPlkApiClient(
    private val delegate: PlkApiClient,
    private val clock: Clock = Clock.System,
) : PlkApiClient by delegate {
    private val mutex = Mutex()
    private val schedules = linkedMapOf<ScheduleKey, CachedSchedule>()

    override suspend fun getSchedules(
        dateFrom: String,
        dateTo: String,
        stations: String,
        carriersInclude: String,
        fullRoutes: Boolean,
    ): PlkScheduleResponse = mutex.withLock {
        val key = ScheduleKey(dateFrom, dateTo, stations, carriersInclude, fullRoutes)
        val now = clock.now()
        schedules.entries.removeAll { (_, entry) -> now < entry.loadedAt || now >= entry.loadedAt + SCHEDULE_TTL }
        schedules.remove(key)?.let { cached ->
            schedules[key] = cached
            return@withLock cached.response
        }

        val response = delegate.getSchedules(dateFrom, dateTo, stations, carriersInclude, fullRoutes)
        val stationCount = response.routes.sumOf { it.stations.size }
        if (stationCount <= MAX_CACHED_STATIONS) {
            while (schedules.isNotEmpty() &&
                (schedules.size >= MAX_CACHED_SCHEDULES ||
                    schedules.values.sumOf { it.stationCount } + stationCount > MAX_CACHED_STATIONS)
            ) {
                schedules.remove(schedules.keys.first())
            }
            schedules[key] = CachedSchedule(response, clock.now(), stationCount)
        }
        response
    }

    private data class ScheduleKey(
        val dateFrom: String,
        val dateTo: String,
        val stations: String,
        val carriersInclude: String,
        val fullRoutes: Boolean,
    )

    private data class CachedSchedule(
        val response: PlkScheduleResponse,
        val loadedAt: Instant,
        val stationCount: Int,
    )
}

private val SCHEDULE_TTL = 1.hours
private const val MAX_CACHED_SCHEDULES = 8
private const val MAX_CACHED_STATIONS = 20_000
