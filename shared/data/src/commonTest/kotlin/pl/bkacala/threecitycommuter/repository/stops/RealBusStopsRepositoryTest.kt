package pl.bkacala.threecitycommuter.repository.stops

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.TransitStopsDao
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class RealTransitStopsRepositoryTest {

    @Test
    fun `refreshes bus stops when cache version is outdated even if timestamp is fresh`() = runTest {
        val gdanskStop = createStop(101, TransitProvider.GDANSK, isForBuses = false, isForTrams = true)
        val gdyniaStop = createStop(202, TransitProvider.GDYNIA, isForBuses = true, isForTrams = false)
        val transitDataSource = FakeTransitDataSource(listOf(gdanskStop, gdyniaStop))
        val transitStopsDao = FakeTransitStopsDao(
            mutableListOf(createStop(101, TransitProvider.GDANSK, isForBuses = false, isForTrams = true).toEntity()),
        )
        val lastUpdateRepository = FakeLastUpdateRepository(
            timestamps = mutableMapOf("transit_stops" to Long.MAX_VALUE),
            values = mutableMapOf("transit_stops_cache_version" to 0),
        )
        val repository = RealTransitStopsRepository(
            transitDataSource = transitDataSource,
            transitStopsDao = transitStopsDao,
            lastUpdateRepository = lastUpdateRepository,
        )

        repository.getTransitStops().test {
            val stops = awaitItem()
            assertEquals(listOf(gdanskStop.stopKey, gdyniaStop.stopKey), stops.map { it.stopKey })
            awaitComplete()
        }

        assertEquals(1, transitDataSource.getStopsCalls)
        assertEquals(5L, lastUpdateRepository.values["transit_stops_cache_version"])
    }

    @Test
    fun `persists bus and tram flags with the stop`() = runTest {
        val gdanskStop = createStop(1001, TransitProvider.GDANSK, isForBuses = false, isForTrams = false)
        val repository = RealTransitStopsRepository(
            transitDataSource = FakeTransitDataSource(listOf(gdanskStop)),
            transitStopsDao = FakeTransitStopsDao(mutableListOf(gdanskStop.toEntity())),
            lastUpdateRepository = FakeLastUpdateRepository(
                timestamps = mutableMapOf("transit_stops" to Long.MAX_VALUE),
                values = mutableMapOf("transit_stops_cache_version" to 2),
            ),
        )

        repository.getTransitStops().test {
            val stops = awaitItem()
            assertEquals(1, stops.size)
            assertEquals(false, stops.single().isForBuses)
            assertEquals(false, stops.single().isForTrams)
            awaitComplete()
        }
    }

    @Test
    fun `seeds local catalog and emits it when network refresh fails`() = runTest {
        val bundledStop = createStop(303, TransitProvider.GDYNIA, isForBuses = true, isForTrams = false)
        val transitDataSource = FakeTransitDataSource(
            stops = emptyList(),
            bundledStops = listOf(bundledStop),
            networkError = IllegalStateException("network unavailable"),
        )
        val transitStopsDao = FakeTransitStopsDao(mutableListOf())
        val repository = RealTransitStopsRepository(
            transitDataSource = transitDataSource,
            transitStopsDao = transitStopsDao,
            lastUpdateRepository = FakeLastUpdateRepository(),
        )

        repository.getTransitStops().test {
            assertEquals(listOf(bundledStop.stopKey), awaitItem().map { it.stopKey })
            awaitComplete()
        }

        assertEquals(1, transitDataSource.getStopsCalls)
        assertEquals(1, transitStopsDao.getRealTransitStops().size)
    }

    @Test
    fun `emits existing catalog when stale network refresh fails`() = runTest {
        val storedStop = createStop(404, TransitProvider.GDANSK, isForBuses = true, isForTrams = false)
        val transitDataSource = FakeTransitDataSource(
            stops = emptyList(),
            networkError = IllegalStateException("network unavailable"),
        )
        val repository = RealTransitStopsRepository(
            transitDataSource = transitDataSource,
            transitStopsDao = FakeTransitStopsDao(mutableListOf(storedStop.toEntity())),
            lastUpdateRepository = FakeLastUpdateRepository(
                timestamps = mutableMapOf("transit_stops" to 0),
                values = mutableMapOf("transit_stops_cache_version" to 5),
            ),
        )

        repository.getTransitStops().test {
            assertEquals(listOf(storedStop.stopKey), awaitItem().map { it.stopKey })
            awaitComplete()
        }
    }

    private fun createStop(
        sourceStopId: Int,
        provider: TransitProvider,
        isForBuses: Boolean,
        isForTrams: Boolean,
    ): TransitStopData {
        return TransitStopData(
            stopKey = TransitStopKey(provider, sourceStopId),
            stopCode = sourceStopId.toString(),
            stopName = "Stop $sourceStopId",
            stopShortName = null,
            stopDesc = null,
            subName = null,
            date = null,
            zoneId = 1,
            zoneName = null,
            virtual = 0,
            nonpassenger = 0,
            depot = 0,
            ticketZoneBorder = 0,
            onDemand = false,
            activationDate = null,
            stopLat = 54.0,
            stopLon = 18.0,
            stopUrl = null,
            locationType = null,
            parentStation = null,
            stopTimezone = null,
            wheelchairBoarding = null,
            isForBuses = isForBuses,
            isForTrams = isForTrams,
        )
    }
}

private class FakeTransitStopsDao(
    private val entities: MutableList<pl.bkacala.threecitycommuter.model.stops.TransitStopEntity>,
) : TransitStopsDao {

    override suspend fun upsertTransitStops(stops: List<pl.bkacala.threecitycommuter.model.stops.TransitStopEntity>) {
        stops.forEach { stop ->
            entities.removeAll { it.provider == stop.provider && it.sourceStopId == stop.sourceStopId }
            entities.add(stop)
        }
    }

    override suspend fun getRealTransitStops(): List<pl.bkacala.threecitycommuter.model.stops.TransitStopEntity> {
        return entities.sortedWith(compareBy({ it.provider }, { it.sourceStopId }))
    }
}

private class FakeLastUpdateRepository(
    val timestamps: MutableMap<String, Long> = mutableMapOf(),
    val values: MutableMap<String, Long> = mutableMapOf(),
) : LastUpdateRepository {

    override fun getLastUpdateTimeStamp(key: String): Long {
        return timestamps[key] ?: 0
    }

    override fun storeLastUpdateCurrentTimeStamp(key: String) {
        timestamps[key] = Long.MAX_VALUE
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return values[key] ?: defaultValue
    }

    override fun putLong(key: String, value: Long) {
        values[key] = value
    }
}

private class FakeTransitDataSource(
    private val stops: List<TransitStopData>,
    private val bundledStops: List<TransitStopData> = emptyList(),
    private val networkError: Throwable? = null,
) : TransitDataSource {
    var getStopsCalls: Int = 0
        private set

    override fun features(provider: TransitProvider) = error("Not needed in test")

    override suspend fun getStops(): List<TransitStopData> {
        getStopsCalls += 1
        networkError?.let { throw it }
        return stops
    }

    override suspend fun getBundledStops(): List<TransitStopData> = bundledStops

    override suspend fun getDepartures(stopKey: TransitStopKey): List<Departure> = emptyList()

    override suspend fun getRouteShape(
        provider: TransitProvider,
        routeId: Int,
        tripId: Int,
    ): Route? = null

    override suspend fun getVehiclePosition(
        provider: TransitProvider,
        vehicleId: Int,
    ): VehiclePosition? = null

    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> = emptyList()
}
