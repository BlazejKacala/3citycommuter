package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.repository.location.LocationRepository

object MockLocationRepository {
    val mockLocationRepository = object: LocationRepository {
        override fun getLocation(): Flow<UserLocation> = flow {
            delay(100)
            emit(UserLocation.default().copy(isFixed = false))
        }
    }
}