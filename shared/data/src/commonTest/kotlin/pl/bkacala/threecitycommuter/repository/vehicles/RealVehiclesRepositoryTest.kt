package pl.bkacala.threecitycommuter.repository.vehicles

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import pl.bkacala.threecitycommuter.client.TransitDataSource
import pl.bkacala.threecitycommuter.dao.VehiclesDao
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehicleEntity
import pl.bkacala.threecitycommuter.model.vehicles.toVehicleEntity
import pl.bkacala.threecitycommuter.repository.update.LastUpdateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RealVehiclesRepositoryTest {
    private val dao = CountingVehiclesDao()
    private val source = VehicleDataSource()
    private val updates = VehicleUpdateTimes()
    private val repository = RealVehiclesRepository(dao, source, updates)

    @Test
    fun `repeated concurrent reads reuse the same fleet and numeric index`() = runTest {
        dao.vehicles = listOf(vehicle("12").toVehicleEntity())
        updates.timestamp = Clock.System.now().epochSeconds
        val results = List(3) { async { repository.getVehiclesByCode(TransitProvider.GDANSK).first() } }.awaitAll()
        assertEquals(1, dao.reads)
        assertEquals(0, source.calls)
        assertSame(results[0], results[1])
        assertEquals("12", results[0].getValue(12).vehicleCode)
    }

    @Test
    fun `explicit refresh updates timestamp and replaces the index without another database read`() = runTest {
        source.vehicles = listOf(vehicle("14"))
        repository.updateVehiclesBase()
        val catalog = repository.getVehiclesByCode(TransitProvider.GDANSK).first()
        assertEquals(setOf(14), catalog.keys)
        assertEquals(1, source.calls)
        assertEquals(0, dao.reads)
    }

    @Test
    fun `failed metadata refresh preserves cached vehicles and backs off`() = runTest {
        dao.vehicles = listOf(vehicle("12").toVehicleEntity())
        source.fail = true
        repeat(2) {
            assertEquals(setOf(12), repository.getVehiclesByCode(TransitProvider.GDANSK).first().keys)
        }
        assertEquals(1, source.calls)
        assertEquals(1, dao.reads)
    }

    private fun vehicle(vehicleCode: String): Vehicle =
        Vehicle(
            photo = "",
            vehicleCode = vehicleCode,
            carrirer = "ZTM",
            transportationType = "BUS",
            vehicleCharacteristics = "",
            bidirectional = false,
            historicVehicle = false,
            length = 12.0,
            brand = "Solaris",
            model = "Urbino",
            productionYear = 2020,
            seats = 30,
            standingPlaces = 70,
            airConditioning = true,
            monitoring = true,
            internalMonitor = true,
            floorHeight = "LOW",
            kneelingMechanism = true,
            wheelchairsRamp = true,
            usb = true,
            voiceAnnouncements = true,
            aed = false,
            bikeHolders = 1,
            ticketMachine = true,
            patron = "",
            url = "",
            passengersDoors = 3,
        )
}

private class CountingVehiclesDao : VehiclesDao {
    var vehicles = emptyList<VehicleEntity>()
    var reads = 0
    override suspend fun getVehicles(): List<VehicleEntity> {
        reads += 1
        return vehicles
    }
    override suspend fun getVehicle(vehicleCode: Int): VehicleEntity = vehicles.first { it.vehicleCode == vehicleCode.toString() }
    override suspend fun upsertVehicles(vehicles: List<VehicleEntity>) {
        this.vehicles = vehicles
    }
}

private class VehicleUpdateTimes : LastUpdateRepository {
    var timestamp = 0L
    override fun getLastUpdateTimeStamp(key: String): Long = timestamp
    override fun storeLastUpdateCurrentTimeStamp(key: String) {
        timestamp = Clock.System.now().epochSeconds
    }
    override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    override fun putLong(key: String, value: Long) = Unit
}

private class VehicleDataSource : TransitDataSource {
    var vehicles = emptyList<Vehicle>()
    var calls = 0
    var fail = false
    override suspend fun getVehicles(provider: TransitProvider): List<Vehicle> {
        calls += 1
        check(!fail) { "Offline" }
        return vehicles
    }
    override fun features(provider: TransitProvider) = error("Unused")
    override suspend fun getStops() = error("Unused")
    override suspend fun getDepartures(stopKey: TransitStopKey) = error("Unused")
    override suspend fun getRouteShape(provider: TransitProvider, routeId: Int, tripId: Int) = error("Unused")
    override suspend fun getVehiclePosition(provider: TransitProvider, vehicleId: Int) = error("Unused")
}
