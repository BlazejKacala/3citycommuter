package pl.bkacala.threecitycommuter.ui.screen.map

import android.Manifest
import app.cash.turbine.test
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import dev.shreyaspatil.permissionFlow.MultiplePermissionState
import dev.shreyaspatil.permissionFlow.PermissionFlow
import dev.shreyaspatil.permissionFlow.PermissionState
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.tools.MainDispatcherRule
import pl.bkacala.threecitycommuter.tools.makeRandomInstance
import pl.bkacala.threecitycommuter.ui.common.UiState
import pl.bkacala.threecitycommuter.ui.screen.map.component.BusStopMapItem

class MapScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockBusStopsRepository = object: BusStopsRepository {

        override fun getBusStops(): Flow<List<BusStopData>> {
            return flowOf(listOf(makeRandomInstance<BusStopData>()))
        }

        override fun getDepartures(stopId: Int): Flow<List<Departure>> {
            return flowOf(listOf(makeRandomInstance<Departure>()))

        }
    }

    private val mockLocationRepository = object: LocationRepository {
        override fun getLocation(): Flow<UserLocation> {
            return flowOf(UserLocation.default())
        }
    }

    private val mockPermissionFlow = object: PermissionFlow {
        override fun getMultiplePermissionState(vararg permissions: String): StateFlow<MultiplePermissionState> {
            throw IllegalStateException("It's not used and therefore shouldn't be called")
        }

        override fun getPermissionState(permission: String): StateFlow<PermissionState> {
            return MutableStateFlow(PermissionState(Manifest.permission.ACCESS_FINE_LOCATION, true))
        }

        override fun notifyPermissionsChanged(vararg permissions: String) {}

        override fun startListening() {}

        override fun stopListening() {}

    }

    private fun mapScreenViewModel() = MapScreenViewModel(
        stopsRepository = mockBusStopsRepository,
        locationRepository = mockLocationRepository,
        permissionFlow = mockPermissionFlow
    )

    @Test
    fun `should load bus stops correctly`() {

        val viewModel = mapScreenViewModel()

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
}
