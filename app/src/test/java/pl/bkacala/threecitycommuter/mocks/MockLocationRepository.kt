package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.repository.location.LocationRepository

object MockLocationRepository {
    val mockLocationRepository = object: LocationRepository {
        override fun getLocation(): Flow<UserLocation> {
            return flowOf(UserLocation.default().copy(isFixed = false))
        }
    }
}