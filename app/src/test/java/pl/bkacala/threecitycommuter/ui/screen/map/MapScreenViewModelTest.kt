package pl.bkacala.threecitycommuter.ui.screen.map

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import pl.bkacala.threecitycommuter.mocks.MockBusStopsRepository.mockBusStopsRepository
import pl.bkacala.threecitycommuter.mocks.MockLocationRepository.mockLocationRepository
import pl.bkacala.threecitycommuter.mocks.MockPermissionFlows.mockGrantedPermissionFlow
import pl.bkacala.threecitycommuter.mocks.MockVehiclesRepository.mockVehiclesRepository
import pl.bkacala.threecitycommuter.tools.MainDispatcherRule
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem
import pl.bkacala.threecitycommuter.usecase.GetDeparturesUseCase
class MapScreenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun mapScreenViewModel() = MapScreenViewModel(
        stopsRepository = mockBusStopsRepository,
        locationRepository = mockLocationRepository,
        permissionFlow = mockGrantedPermissionFlow,
        vehiclesRepository = mockVehiclesRepository,
        getDeparturesUseCase = GetDeparturesUseCase(
            busStopsRepository = mockBusStopsRepository,
            vehiclesRepository = mockVehiclesRepository,
        )
    )

    @Test
    fun `should load bus stops correctly`() {
        val viewModel = mapScreenViewModel()
        runTest {
            val job =
                launch {
                    viewModel.busStops.test {
                        val initialItem = awaitItem()
                        initialItem.shouldBeInstanceOf<UiState.Loading>()

                        val loadedBusStations = awaitItem()
                        loadedBusStations.shouldBeInstanceOf<UiState.Success<List<BusStopMapItem>>>()

                        cancelAndIgnoreRemainingEvents()
                    }
                }

            job.join()
            job.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `should receive default location when permission is not granted`() {
        val viewModel = mapScreenViewModel()
        runTest {
            val job =
                launch {
                    viewModel.location.test {
                        val location = awaitItem()
                        location.isFixed shouldBe true
                        ensureAllEventsConsumed()
                    }
                }
            job.join()
            job.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `should receive real location when permission is granted`() {
        runTest {
            val viewModel = mapScreenViewModel()
            val job =
                launch {
                    viewModel.location.test {
                        viewModel.startTracingJobs()

                        val initialLocation = awaitItem()
                        initialLocation.isFixed shouldBe true

                        val fusedLocation = awaitItem()
                        fusedLocation.isFixed shouldBe false

                        viewModel.startTracingJobs()
                        cancelAndIgnoreRemainingEvents()
                    }
                }

            job.join()
            job.cancel()
        }
    }

    @Test
    fun `should pick closest bus stop when data is loaded`() {
        runTest {
            val viewModel = mapScreenViewModel()
            val job =
                launch {
                    viewModel.departures.filter { it?.departures?.isNotEmpty() ?: false }.test {
                        val item = awaitItem()
                        item?.departures?.size shouldNotBe 0

                        cancelAndIgnoreRemainingEvents()
                    }
                }

            job.join()
            job.cancel()
        }
    }
}
