package pl.bkacala.threecitycommuter.ui.screen.map

import dev.shreyaspatil.permissionFlow.MultiplePermissionState
import dev.shreyaspatil.permissionFlow.PermissionFlow
import dev.shreyaspatil.permissionFlow.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import android.Manifest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import pl.bkacala.threecitycommuter.tools.MainDispatcherRule
import pl.bkacala.threecitycommuter.tools.makeRandomInstance

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

    @Test
    fun `should load bus stops correctly`() {
        val viewModel = MapScreenViewModel(
            stopsRepository = mockBusStopsRepository,
            locationRepository = mockLocationRepository,
            permissionFlow = mockPermissionFlow
        )
        runTest {
            val a = viewModel.busStops.take(1).toList()
            //TODO make test
        }

    }
}
