package pl.bkacala.threecitycommuter.ui.screen.map

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import pl.bkacala.threecitycommuter.mocks.MockBusStopsRepository.mockBusStopsRepository
import pl.bkacala.threecitycommuter.mocks.MockLocationRepository.mockLocationRepository
import pl.bkacala.threecitycommuter.mocks.MockPermissionFlows.mockDeniedPermissionFlow
import pl.bkacala.threecitycommuter.mocks.MockPermissionFlows.mockGrantedPermissionFlow
import pl.bkacala.threecitycommuter.mocks.MockRoutesRepository.mockRoutesRepository
import pl.bkacala.threecitycommuter.mocks.MockVehiclesRepository.mockVehiclesRepository
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.BusStopType
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.tools.MainDispatcherRule
import pl.bkacala.threecitycommuter.tools.makeRandomInstance
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        stopsRepository: BusStopsRepository = mockBusStopsRepository,
        locationRepository: LocationRepository = mockLocationRepository,
        vehiclesRepository: VehiclesRepository = mockVehiclesRepository,
    ) = MapScreenViewModel(
        stopsRepository = stopsRepository,
        locationRepository = locationRepository,
        permissionFlow = mockGrantedPermissionFlow,
        vehiclesRepository = vehiclesRepository,
        getDeparturesUseCase = GetDeparturesUseCase(stopsRepository, vehiclesRepository),
        routesRepository = mockRoutesRepository,
    )

    // ——— Initial state ———

    @Test
    fun `busStops is Loading before repository emits`() =
        runTest {
            val vm = createViewModel()
            vm.busStops.value.shouldBeInstanceOf<UiState.Loading>()
        }

    @Test
    fun `busStops becomes Success after repository emits`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            vm.busStops.value.shouldBeInstanceOf<UiState.Success<*>>()
        }

    @Test
    fun `busStops contains mapped items from repository`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            val state = vm.busStops.value as UiState.Success
            state.data.size shouldBe 1
        }

    @Test
    fun `busStops becomes Error when repository throws`() =
        runTest {
            val throwingRepository =
                object : BusStopsRepository {
                    override fun getBusStops(): Flow<List<BusStopData>> =
                        flow {
                            throw RuntimeException("Network error")
                        }

                    override fun getDepartures(stopId: Int): Flow<List<Departure>> = mockBusStopsRepository.getDepartures(stopId)

                    override fun storeBusStopsTypes(types: List<BusStopType>) {}
                }

            val vm = createViewModel(stopsRepository = throwingRepository)
            advanceTimeBy(50)
            vm.busStops.value.shouldBeInstanceOf<UiState.Error>()
        }

    // ——— Location & centerOnPositionVisibility ———

    @Test
    fun `location starts at default fixed position`() =
        runTest {
            val vm = createViewModel()
            vm.location.value shouldBe UserLocation.default()
        }

    @Test
    fun `centerOnPositionVisibility is false when location is fixed`() =
        runTest {
            val vm = createViewModel()
            vm.centerOnPositionVisibility.value shouldBe false
        }

    @Test
    fun `centerOnPositionVisibility becomes true after location updates to non-fixed`() =
        runTest {
            val vm = createViewModel()
            vm.centerOnPositionVisibility.test {
                awaitItem() shouldBe false
                vm.startTracingJobs()
                advanceTimeBy(200)
                awaitItem() shouldBe true
            }
        }

    @Test
    fun `location does not update when permission is denied`() =
        runTest {
            val vm =
                MapScreenViewModel(
                    stopsRepository = mockBusStopsRepository,
                    locationRepository = mockLocationRepository,
                    permissionFlow = mockDeniedPermissionFlow,
                    vehiclesRepository = mockVehiclesRepository,
                    getDeparturesUseCase = GetDeparturesUseCase(mockBusStopsRepository, mockVehiclesRepository),
                    routesRepository = mockRoutesRepository,
                )
            vm.startTracingJobs()
            advanceTimeBy(500)
            vm.location.value shouldBe UserLocation.default()
        }

    // ——— onBusStopSelected ———

//    @Test
//    fun `onBusStopSelected sets selectedBusStop`() = runTest {
//        val vm = createViewModel()
//        advanceTimeBy(200)
//        val stop = busStopsFrom(vm).first()
//
//        vm.onBusStopSelected(stop)
//
//        vm.selectedBusStop.value shouldBe stop
//    }

    @Test
    fun `onBusStopSelected clears trackedVehicle and route`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            val stop = busStopsFrom(vm).first()

            vm.onBusStopSelected(stop)

            vm.trackedVehicle.value shouldBe null
            vm.route.value shouldBe null
        }

    @Test
    fun `onBusStopSelected loads departures`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            val stop = busStopsFrom(vm).first()

            vm.onBusStopSelected(stop)
            advanceTimeBy(300)

            vm.departures.value shouldNotBe null
        }

    @Test
    fun `departures header contains stop name`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            val stop = busStopsFrom(vm).first()

            vm.onBusStopSelected(stop)
            advanceTimeBy(300)

            vm.departures.value
                ?.header
                ?.busStopName shouldBe stop.data.name
        }

    @Test
    fun `selecting a second stop replaces departures from first stop`() =
        runTest {
            val firstStop = busStopAt(lat = 54.35, lon = 18.64, id = 1)
            val secondStop = busStopAt(lat = 54.36, lon = 18.65, id = 2)
            val repository = repositoryWithStops(listOf(firstStop, secondStop))
            val vm = createViewModel(stopsRepository = repository)
            advanceTimeBy(200)

            val mapItems = busStopsFrom(vm)
            vm.onBusStopSelected(mapItems[0])
            advanceTimeBy(300)
            val firstDepartures = vm.departures.value

            vm.onBusStopSelected(mapItems[1])
            advanceTimeBy(300)

            vm.departures.value
                ?.header
                ?.busStopName shouldBe secondStop.stopName
            vm.departures.value shouldNotBe firstDepartures
        }

    // ——— onMapClicked ———

    @Test
    fun `onMapClicked clears selectedBusStop`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            vm.onBusStopSelected(busStopsFrom(vm).first())

            vm.onMapClicked()

            vm.selectedBusStop.value shouldBe null
        }

    @Test
    fun `onMapClicked clears departures`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            vm.onBusStopSelected(busStopsFrom(vm).first())
            advanceTimeBy(300)

            vm.onMapClicked()

            vm.departures.value shouldBe null
        }

    @Test
    fun `onMapClicked clears trackedVehicle and route`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            vm.onBusStopSelected(busStopsFrom(vm).first())

            vm.onMapClicked()

            vm.trackedVehicle.value shouldBe null
            vm.route.value shouldBe null
        }

    // ——— stopTracingJobs / startTracingJobs ———

    @Test
    fun `stopTracingJobs does not change selectedBusStop`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            val stop = busStopsFrom(vm).first()
            vm.onBusStopSelected(stop)

            vm.stopTracingJobs()

            vm.selectedBusStop.value shouldBe stop
        }

    @Test
    fun `startTracingJobs updates location to non-fixed`() =
        runTest {
            val vm = createViewModel()
            vm.startTracingJobs()
            advanceTimeBy(200)

            vm.location.value.isFixed shouldBe false
        }

    @Test
    fun `stopTracingJobs stops location updates`() =
        runTest {
            val vm = createViewModel()
            vm.startTracingJobs()
            advanceTimeBy(200)
            val locationAfterStart = vm.location.value

            vm.stopTracingJobs()
            advanceTimeBy(200)

            vm.location.value shouldBe locationAfterStart
        }

    // ——— showClosestStationBoard / centerOnUserPosition ———

    @Test
    fun `showClosestStationBoard auto-selects closest stop when location is available`() =
        runTest {
            val vm = createViewModel()
            vm.startTracingJobs()
            advanceTimeBy(300)

            vm.selectedBusStop.value shouldNotBe null
        }

    @Test
    fun `centerOnUserPosition triggers closest station selection`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            vm.startTracingJobs()
            advanceTimeBy(200)

            vm.centerOnUserPosition()
            advanceTimeBy(100)

            vm.selectedBusStop.value shouldNotBe null
        }

    // ——— onMapReloadRequest ———

    @Test
    fun `onMapReloadRequest reloads bus stops`() =
        runTest {
            val vm = createViewModel()
            advanceTimeBy(200)
            vm.busStops.value.shouldBeInstanceOf<UiState.Success<*>>()

            vm.onMapReloadRequest()
            advanceTimeBy(200)

            vm.busStops.value.shouldBeInstanceOf<UiState.Success<*>>()
        }

    // ——— Error propagation ———

    @Test
    fun `errors flow receives error when departures fail`() =
        runTest {
            val throwingRepository =
                object : BusStopsRepository {
                    override fun getBusStops() = mockBusStopsRepository.getBusStops()

                    override fun getDepartures(stopId: Int): Flow<List<Departure>> =
                        flow {
                            throw RuntimeException("Departures unavailable")
                        }

                    override fun storeBusStopsTypes(types: List<BusStopType>) {}
                }

            val vm = createViewModel(stopsRepository = throwingRepository)
            advanceTimeBy(200)
            val stop = busStopsFrom(vm).first()

            vm.errors.test {
                vm.onBusStopSelected(stop)
                val error = awaitItem()
                error.message shouldBe "Departures unavailable"
            }
        }

    // ——— Helpers ———

    private fun busStopsFrom(vm: MapScreenViewModel): List<BusStopMapItem> = (vm.busStops.value as UiState.Success).data

    private fun busStopAt(
        lat: Double,
        lon: Double,
        id: Int,
    ): BusStopData = makeRandomInstance<BusStopData>().copy(stopId = id, stopLat = lat, stopLon = lon)

    private fun repositoryWithStops(stops: List<BusStopData>): BusStopsRepository =
        object : BusStopsRepository {
            override fun getBusStops(): Flow<List<BusStopData>> = flowOf(stops)

            override fun getDepartures(stopId: Int): Flow<List<Departure>> = mockBusStopsRepository.getDepartures(stopId)

            override fun storeBusStopsTypes(types: List<BusStopType>) {}
        }
}
