package pl.bkacala.threecitycommuter.repository.stops

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.toEntity
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class RealBusStopsRepositoryTest {

    @Test
    fun `refreshes bus stops when cache version is outdated even if timestamp is fresh`() = runTest {
        val gdanskStop = createStop(101, TransitProvider.GDANSK, isForBuses = false, isForTrams = true)
        val gdyniaStop = createStop(202, TransitProvider.GDYNIA, isForBuses = true, isForTrams = false)
        val transitDataSource = FakeTransitDataSource(listOf(gdanskStop, gdyniaStop))
        val busStopsDao = FakeBusStopsDao(
            mutableListOf(createStop(101, TransitProvider.GDANSK, isForBuses = false, isForTrams = true).toEntity()),
        )
        val lastUpdateRepository = FakeLastUpdateRepository(
            timestamps = mutableMapOf("bus_stops" to Long.MAX_VALUE),
            values = mutableMapOf("bus_stops_cache_version" to 0),
        )
        val repository = RealBusStopsRepository(
            transitDataSource = transitDataSource,
            busStopsDao = busStopsDao,
            lastUpdateRepository = lastUpdateRepository,
        )

        repository.getBusStops().test {
            val stops = awaitItem()
            assertEquals(listOf(gdanskStop.stopKey, gdyniaStop.stopKey), stops.map { it.stopKey })
            awaitComplete()
        }

        assertEquals(1, transitDataSource.getStopsCalls)
        assertEquals(5L, lastUpdateRepository.values["bus_stops_cache_version"])
    }

    @Test
    fun `persists bus and tram flags with the stop`() = runTest {
        val gdanskStop = createStop(1001, TransitProvider.GDANSK, isForBuses = false, isForTrams = false)
        val repository = RealBusStopsRepository(
            transitDataSource = FakeTransitDataSource(listOf(gdanskStop)),
            busStopsDao = FakeBusStopsDao(mutableListOf(gdanskStop.toEntity())),
            lastUpdateRepository = FakeLastUpdateRepository(
                timestamps = mutableMapOf("bus_stops" to Long.MAX_VALUE),
                values = mutableMapOf("bus_stops_cache_version" to 2),
            ),
        )

        repository.getBusStops().test {
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
        val busStopsDao = FakeBusStopsDao(mutableListOf())
        val repository = RealBusStopsRepository(
            transitDataSource = transitDataSource,
            busStopsDao = busStopsDao,
            lastUpdateRepository = FakeLastUpdateRepository(),
        )

        repository.getBusStops().test {
            assertEquals(listOf(bundledStop.stopKey), awaitItem().map { it.stopKey })
            awaitComplete()
        }

        assertEquals(1, transitDataSource.getStopsCalls)
        assertEquals(1, busStopsDao.getRealBusStations().size)
    }

    @Test
    fun `emits existing catalog when stale network refresh fails`() = runTest {
        val storedStop = createStop(404, TransitProvider.GDANSK, isForBuses = true, isForTrams = false)
        val transitDataSource = FakeTransitDataSource(
            stops = emptyList(),
            networkError = IllegalStateException("network unavailable"),
        )
        val repository = RealBusStopsRepository(
            transitDataSource = transitDataSource,
            busStopsDao = FakeBusStopsDao(mutableListOf(storedStop.toEntity())),
            lastUpdateRepository = FakeLastUpdateRepository(
                timestamps = mutableMapOf("bus_stops" to 0),
                values = mutableMapOf("bus_stops_cache_version" to 5),
            ),
        )

        repository.getBusStops().test {
            assertEquals(listOf(storedStop.stopKey), awaitItem().map { it.stopKey })
            awaitComplete()
        }
    }

    private fun createStop(
        sourceStopId: Int,
        provider: TransitProvider,
        isForBuses: Boolean,
        isForTrams: Boolean,
    ): BusStopData {
        return BusStopData(
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

private class FakeBusStopsDao(
    private val entities: MutableList<pl.bkacala.threecitycommuter.model.stops.BusStopEntity>,
) : BusStopsDao {

    override suspend fun upsertBusStations(stops: List<pl.bkacala.threecitycommuter.model.stops.BusStopEntity>) {
        stops.forEach { stop ->
            entities.removeAll { it.provider == stop.provider && it.sourceStopId == stop.sourceStopId }
            entities.add(stop)
        }
    }

    override suspend fun getRealBusStations(): List<pl.bkacala.threecitycommuter.model.stops.BusStopEntity> {
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
    private val stops: List<BusStopData>,
    private val bundledStops: List<BusStopData> = emptyList(),
    private val networkError: Throwable? = null,
) : TransitDataSource {
    var getStopsCalls: Int = 0
        private set

    override fun features(provider: TransitProvider) = error("Not needed in test")

    override suspend fun getStops(): List<BusStopData> {
        getStopsCalls += 1
        networkError?.let { throw it }
        return stops
    }

    override suspend fun getBundledStops(): List<BusStopData> = bundledStops

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
