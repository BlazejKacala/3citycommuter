package pl.bkacala.threecitycommuter.ui.screen.map

import app.cash.turbine.test
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
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
import pl.bkacala.threecitycommuter.mocks.MockPermissionFlows.mockDeniedPermissionFlow
import pl.bkacala.threecitycommuter.mocks.MockPermissionFlows.mockGrantedPermissionFlow
import pl.bkacala.threecitycommuter.tools.MainDispatcherRule
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem

class MapScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `should load bus stops correctly`() {

        val viewModel = MapScreenViewModel(
            stopsRepository = mockBusStopsRepository,
            locationRepository = mockLocationRepository,
            permissionFlow = mockGrantedPermissionFlow
        )

        runTest {
            val job = launch {
                viewModel.busStops.test {
                    val initialItem = awaitItem()
                    initialItem.shouldBeInstanceOf<UiState.Loading>()

                    val loadedBusStations = awaitItem()
                    loadedBusStations.shouldBeInstanceOf<UiState.Success<List<BusStopMapItem>>>()

                    cancelAndIgnoreRemainingEvents()
                }
            }

            launch {
                viewModel.onMapMoved(
                    LatLngBounds(
                        LatLng(54.3543727, 18.5870928),
                        LatLng(54.3780752, 18.639192)
                    )
                )
            }

            job.join()
            job.cancel()
        }

    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `should receive default location when permission is not granted`() {

        val viewModel = MapScreenViewModel(
            stopsRepository = mockBusStopsRepository,
            locationRepository = mockLocationRepository,
            permissionFlow = mockDeniedPermissionFlow
        )

        runTest {
            val job = launch {
                viewModel.location.test {
                    val location = awaitItem()
                    advanceTimeBy(200)
                    location.isFixed shouldBe true
                    ensureAllEventsConsumed()
                }
            }


            job.join()
            job.cancel()
        }
    }

    @Test
    fun `should receive real location when permission is granted`() {

        val viewModel = MapScreenViewModel(
            stopsRepository = mockBusStopsRepository,
            locationRepository = mockLocationRepository,
            permissionFlow = mockGrantedPermissionFlow
        )

        runTest {
            val job = launch {
                viewModel.location.test {

                    val initialLocation = awaitItem()
                    initialLocation.isFixed shouldBe true

                    val fusedLocation = awaitItem()
                    fusedLocation.isFixed shouldBe false

                    cancelAndIgnoreRemainingEvents()
                }
            }

            job.join()
            job.cancel()
        }
    }

    @Test
    fun `should pick closest bus stop when data is loaded`() {

        val viewModel = MapScreenViewModel(
            stopsRepository = mockBusStopsRepository,
            locationRepository = mockLocationRepository,
            permissionFlow = mockGrantedPermissionFlow
        )

        runTest {
            val job = launch {
                viewModel.departures.filter { it.isNotEmpty() }.test {
                    val item = awaitItem()
                    item.size shouldNotBe 0

                    cancelAndIgnoreRemainingEvents()
                }
            }
            launch {
                viewModel.onMapMoved(
                    LatLngBounds(
                        LatLng(54.3543727, 18.5870928),
                        LatLng(54.3780752, 18.679192)
                    )
                )
            }

            job.join()
            job.cancel()
        }
    }
}
