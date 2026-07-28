package pl.bkacala.threecitycommuter.ui.screen.map

import app.cash.turbine.test
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.stops.BusStopType
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopId
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<MapScreenViewModel>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { viewModel ->
            viewModel.onAction(MapAction.ScreenPaused)
            viewModel.onAction(MapAction.MapClicked)
        }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN bus stops repository emits data WHEN view model initializes THEN bus stops state becomes success with mapped items`() =
        runTest {
            val busStop = busStopData(stopId = 11, name = "Dworzec Glowny")
            val viewModel = createViewModel(stopsRepository = FakeBusStopsRepository(stops = listOf(busStop)))

            advanceTimeBy(150.milliseconds)

            val state = viewModel.uiState.value.busStops.shouldBeInstanceOf<UiState.Success<List<BusStopMapItem>>>()
            state.data.shouldHaveSize(1)
            state.data.first().data shouldBe busStop
        }

    @Test
    fun `GIVEN location permission is denied WHEN tracing starts THEN location stays at default and center button stays hidden`() =
        runTest {
            val viewModel =
                createViewModel(
                    permissionChecker = FakePermissionChecker(isGranted = false),
                    locationRepository = FakeLocationRepository(UserLocation.default().copy(isFixed = false)),
                )

            viewModel.onAction(MapAction.ScreenResumed)
            advanceTimeBy(500.milliseconds)

            viewModel.uiState.value.userLocation shouldBe UserLocation.default()
            viewModel.uiState.value.showCenterOnPositionButton shouldBe false
            viewModel.onAction(MapAction.ScreenPaused)
        }

    @Test
    fun `GIVEN selected stop and loaded departures WHEN map is clicked THEN selection dependent state is cleared`() =
        runTest {
            val viewModel = createViewModel()
            advanceTimeBy(150.milliseconds)
            val stop = busStopsFrom(viewModel).first()
            viewModel.onAction(MapAction.StopSelected(stop.id))
            advanceTimeBy(250.milliseconds)

            viewModel.onAction(MapAction.MapClicked)

            viewModel.uiState.value.selectedBusStop shouldBe null
            viewModel.uiState.value.selectedDeparture shouldBe null
            viewModel.uiState.value.departures shouldBe null
            viewModel.uiState.value.trackedVehicle shouldBe null
            viewModel.uiState.value.route shouldBe null
            viewModel.onAction(MapAction.ScreenPaused)
        }

    @Test
    fun `GIVEN stop is selected WHEN departures request completes THEN bottom sheet is exposed for selected stop`() =
        runTest {
            val selectedStop = busStopData(stopId = 17, name = "Brzezno")
            val viewModel = createViewModel(stopsRepository = FakeBusStopsRepository(stops = listOf(selectedStop)))

            advanceTimeBy(150.milliseconds)
            viewModel.onAction(MapAction.StopSelected(busStopsFrom(viewModel).first().id))
            advanceTimeBy(250.milliseconds)

            viewModel.uiState.value.selectedBusStop?.data shouldBe selectedStop
            viewModel.uiState.value.departures shouldNotBe null
            viewModel.uiState.value.departures?.header?.busStopName shouldBe selectedStop.name
            viewModel.onAction(MapAction.ScreenPaused)
        }

    @Test
    fun `GIVEN real user location becomes available WHEN closest stop board opens automatically THEN selected stop is focused on the map`() =
        runTest {
            val nearestStop = busStopData(stopId = 17, name = "Brzezno")
            val viewModel = createViewModel(stopsRepository = FakeBusStopsRepository(stops = listOf(nearestStop)))

            viewModel.effects.test {
                viewModel.onAction(MapAction.ScreenResumed)
                advanceTimeBy(350.milliseconds)

                awaitItem() shouldBe MapEffect.FocusCamera(BusStopMapItem(nearestStop).position)
                viewModel.uiState.value.selectedBusStop?.data shouldBe nearestStop
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onAction(MapAction.ScreenPaused)
        }

    @Test
    fun `GIVEN view model is initialized WHEN no tracing has started THEN default location and center button state are exposed`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.value.userLocation shouldBe UserLocation.default()
            viewModel.uiState.value.showCenterOnPositionButton shouldBe false
        }

    @Test
    fun `GIVEN map reload is requested WHEN bus stops are loaded again THEN repository is called another time`() =
        runTest {
            val repository = FakeBusStopsRepository()
            val viewModel = createViewModel(stopsRepository = repository)
            advanceTimeBy(150.milliseconds)

            repository.getBusStopsCalls shouldBe 1

            viewModel.onAction(MapAction.ReloadClicked)
            advanceTimeBy(150.milliseconds)

            repository.getBusStopsCalls shouldBe 2
            viewModel.onAction(MapAction.ScreenPaused)
        }

    @Test
    fun `GIVEN departures request fails WHEN stop is selected THEN error effect emits repository error message`() =
        runTest {
            val expectedMessage = "Departures unavailable"
            val viewModel =
                createViewModel(
                    stopsRepository = FakeBusStopsRepository(departuresError = RuntimeException(expectedMessage)),
                )
            advanceTimeBy(150.milliseconds)

            viewModel.effects.test {
                viewModel.onAction(MapAction.StopSelected(busStopsFrom(viewModel).first().id))

                awaitItem() shouldBe MapEffect.ShowError("Nie udało się wczytać danych")
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onAction(MapAction.ScreenPaused)
        }

    private fun createViewModel(
        stopsRepository: BusStopsRepository = FakeBusStopsRepository(),
        locationRepository: LocationRepository = FakeLocationRepository(UserLocation.default().copy(isFixed = false)),
        permissionChecker: PermissionChecker = FakePermissionChecker(isGranted = true),
        vehiclesRepository: VehiclesRepository = FakeVehiclesRepository(),
        routesRepository: RoutesRepository = FakeRoutesRepository(),
    ): MapScreenViewModel {
        val viewModel =
            MapScreenViewModel(
                stopsRepository = stopsRepository,
                locationRepository = locationRepository,
                permissionChecker = permissionChecker,
                vehiclesRepository = vehiclesRepository,
                getDeparturesUseCase = GetDeparturesUseCase(stopsRepository, vehiclesRepository),
                routesRepository = routesRepository,
            )
        createdViewModels += viewModel
        return viewModel
    }

    private fun busStopsFrom(viewModel: MapScreenViewModel): List<BusStopMapItem> =
        (viewModel.uiState.value.busStops as UiState.Success).data

    private fun busStopData(
        stopId: Int = 1,
        name: String = "Test Stop",
        lat: Double = 54.372158,
        lon: Double = 18.638306,
        provider: TransitProvider = TransitProvider.GDANSK,
    ): BusStopData =
        BusStopData(
            stopId = TransitStopId.toAppId(provider, stopId),
            stopCode = "SC$stopId",
            stopName = name,
            stopShortName = name,
            stopDesc = null,
            subName = null,
            date = null,
            zoneId = 1,
            zoneName = "A",
            virtual = 0,
            nonpassenger = 0,
            depot = 0,
            ticketZoneBorder = 0,
            onDemand = false,
            activationDate = null,
            stopLat = lat,
            stopLon = lon,
            stopUrl = null,
            locationType = null,
            parentStation = null,
            stopTimezone = null,
            wheelchairBoarding = null,
            isForBuses = true,
            isForTrams = true,
        )

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

    private inner class FakeBusStopsRepository(
        private val stops: List<BusStopData> = listOf(busStopData()),
        private val departures: List<Departure> = emptyList(),
        private val departuresError: Throwable? = null,
    ) : BusStopsRepository {

        var getBusStopsCalls = 0
            private set

        override fun getBusStops(): Flow<List<BusStopData>> = flow {
            getBusStopsCalls++
            delay(100)
            emit(stops)
        }

        override fun getDepartures(stopId: Int): Flow<List<Departure>> = flow {
            delay(100)
            departuresError?.let { throw it }
            emit(departures)
        }

        override suspend fun storeBusStopsTypes(types: List<BusStopType>) = Unit
    }

    private inner class FakeLocationRepository(
        private val location: UserLocation,
    ) : LocationRepository {

        override fun getLocation(): Flow<UserLocation> = flow {
            delay(100)
            emit(location)
        }
    }

    private inner class FakePermissionChecker(
        private val isGranted: Boolean,
    ) : PermissionChecker {
        override fun isLocationPermissionGranted(): Boolean = isGranted
    }

    private inner class FakeVehiclesRepository(
        private val vehicles: List<Vehicle> = listOf(vehicle(vehicleCode = "1")),
    ) : VehiclesRepository {

        override suspend fun updateVehiclesBase() = Unit

        override fun getVehicle(id: Int): Flow<Vehicle> = flowOf(vehicles.first())

        override fun getVehicles(provider: TransitProvider): Flow<List<Vehicle>> = flow {
            delay(100)
            emit(vehicles)
        }

        override fun getVehiclePosition(provider: TransitProvider, vehicleId: Int): Flow<VehiclePosition?> = flow {
            delay(100)
            emit(null)
        }
    }

    private inner class FakeRoutesRepository(
        private val route: Route = Route(
            listOf(
                Route.GeoPoint(54.372158, 18.638306),
                Route.GeoPoint(54.351959, 18.648064),
            ),
        ),
    ) : RoutesRepository {
        override fun getRoute(provider: TransitProvider, routeId: Int, tripId: Int): Flow<Route> =
            flowOf(route)
    }
}
